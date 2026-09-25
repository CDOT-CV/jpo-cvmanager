package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.postgres.tables.*;
import us.dot.its.jpo.ode.api.models.storage.*;
import us.dot.its.jpo.ode.api.models.storage.FirmwareRuleModels.*;
import us.dot.its.jpo.ode.api.repositories.*;
import us.dot.its.jpo.ode.api.services.FirmwareRuleService.FirmwareRuleConflictException;
import us.dot.its.jpo.ode.api.storage.*;

@SpringBootTest(properties = "firmware-upload.cleanup.enabled=false")
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
@Transactional
class FirmwareRuleServiceTest {
    @Autowired private FirmwareRuleService service;
    @Autowired private FirmwareImageRepository images;
    @Autowired private FirmwareUploadRepository uploads;
    @Autowired private FirmwareUpgradeRuleRepository rules;
    @Autowired private ManufacturerRepository manufacturers;
    @Autowired private RsuModelRepository models;
    @MockitoBean private ObjectStorageServiceRegistry storage;
    private ObjectStorageService provider;
    private RsuModel model;
    private FirmwareImage source;
    private FirmwareImage otherSource;
    private FirmwareImage destination;

    @BeforeEach
    void setUp() {
        provider = mock(ObjectStorageService.class);
        when(storage.getActiveService()).thenReturn(provider);
        var manufacturer = new Manufacturer();
        manufacturer.setName("Rules-" + UUID.randomUUID());
        manufacturers.saveAndFlush(manufacturer);
        model = new RsuModel();
        model.setName("Rules-" + UUID.randomUUID());
        model.setSupportedRadio("C-V2X");
        model.setManufacturer(manufacturer);
        models.saveAndFlush(model);
        source = image("v1", false);
        otherSource = image("v2", false);
        destination = image("v3", true);
    }

    @Test
    void assignsSeveralLegacySourcesWithoutCreatingVerificationEvidence() {
        service.assign(destination.getId(), assignments(new Assignment(source.getId(), null),
                new Assignment(otherSource.getId(), null)));
        assertThat(rules.findFirstByFrom_Id(source.getId()).orElseThrow().getTo().getId()).isEqualTo(destination.getId());
        assertThat(rules.findFirstByFrom_Id(otherSource.getId()).orElseThrow().getTo().getId()).isEqualTo(destination.getId());
        assertThat(images.findById(source.getId()).orElseThrow().getVerifiedUpload()).isNull();
        assertThat(service.options(destination.getId()).canTarget()).isTrue();
    }

    @Test
    void explicitlyReassignsAnExistingLegacyPathWithoutAddingADuplicate() {
        var existing = rule(source, otherSource);
        service.assign(destination.getId(), assignments(new Assignment(source.getId(), otherSource.getId())));
        var updated = rules.findFirstByFrom_Id(source.getId()).orElseThrow();
        assertThat(updated.getId()).isEqualTo(existing.getId());
        assertThat(updated.getTo().getId()).isEqualTo(destination.getId());
    }

    @Test
    void staleSelectionRejectsTheWholeBatch() {
        rule(otherSource, source);
        assertThatThrownBy(() -> service.assign(destination.getId(), assignments(
                new Assignment(source.getId(), null), new Assignment(otherSource.getId(), null))))
                .isInstanceOf(FirmwareRuleConflictException.class).hasMessageContaining("changed");
        assertThat(rules.findFirstByFrom_Id(source.getId())).isEmpty();
        assertThat(rules.findFirstByFrom_Id(otherSource.getId()).orElseThrow().getTo().getId()).isEqualTo(source.getId());
    }

    @Test
    void rejectsSelfPaths() {
        assertThatThrownBy(() -> service.assign(destination.getId(), assignments(new Assignment(destination.getId(), null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateSources() {
        assertThatThrownBy(() -> service.assign(destination.getId(), assignments(
                new Assignment(source.getId(), null), new Assignment(source.getId(), null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rules.findFirstByFrom_Id(source.getId())).isEmpty();
    }

    @Test
    void rejectsSourcesFromDifferentModels() {
        var otherModel = new RsuModel();
        otherModel.setName("Other-" + UUID.randomUUID());
        otherModel.setSupportedRadio("C-V2X");
        otherModel.setManufacturer(model.getManufacturer());
        models.saveAndFlush(otherModel);
        otherSource.setModel(otherModel);
        images.saveAndFlush(otherSource);
        assertThatThrownBy(() -> service.assign(destination.getId(), assignments(new Assignment(otherSource.getId(), null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rules.findFirstByFrom_Id(source.getId())).isEmpty();
    }

    @Test
    void rejectsUnverifiedDestinations() {
        assertThatThrownBy(() -> service.assign(otherSource.getId(), assignments(new Assignment(source.getId(), null))))
                .isInstanceOf(FirmwareRuleConflictException.class);
    }

    @Test
    void rejectsMissingDestinations() {
        when(provider.getObjectMetadata(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assign(destination.getId(), assignments(new Assignment(source.getId(), null))))
                .isInstanceOf(FirmwareRuleConflictException.class);
    }

    @Test
    void rejectsChangedObjectVersions() {
        when(provider.getObjectMetadata(any(), any())).thenReturn(Optional.of(
                new StoredObjectMetadata(9L, new ObjectChecksum("CRC32C", "ImIEBA=="), "changed")));
        assertThatThrownBy(() -> service.assign(destination.getId(), assignments(new Assignment(source.getId(), null))))
                .isInstanceOf(FirmwareRuleConflictException.class);
        assertThat(rules.findFirstByFrom_Id(source.getId())).isEmpty();
    }

    @Test
    void rejectsMismatchedObjectMetadataEvenWhenTheVersionMatches() {
        when(provider.getObjectMetadata(any(), any())).thenReturn(Optional.of(
                new StoredObjectMetadata(8L, new ObjectChecksum("CRC32C", "ImIEBA=="), "17")));
        assertThat(service.options(destination.getId()).canTarget()).isFalse();
        assertThatThrownBy(() -> service.assign(destination.getId(), assignments(new Assignment(source.getId(), null))))
                .isInstanceOf(FirmwareRuleConflictException.class);
        when(provider.getObjectMetadata(any(), any())).thenReturn(Optional.of(
                new StoredObjectMetadata(9L, new ObjectChecksum("CRC32C", "AAAAAA=="), "17")));
        assertThatThrownBy(() -> service.assign(destination.getId(), assignments(new Assignment(source.getId(), null))))
                .isInstanceOf(FirmwareRuleConflictException.class);
        assertThat(rules.findFirstByFrom_Id(source.getId())).isEmpty();
    }

    @Test
    void preservesAndAllowsDeletionOfLegacyRulesWithoutCloudVerification() {
        var legacy = rule(source, otherSource);
        assertThat(service.list()).anySatisfy(item -> {
            assertThat(item.ruleId()).isEqualTo(legacy.getId());
            assertThat(item.legacyDestination()).isTrue();
            assertThat(item.source().version()).isEqualTo("v1");
            assertThat(item.destination().version()).isEqualTo("v2");
        });
        assertThat(service.options(otherSource.getId()).canTarget()).isFalse();
        service.delete(legacy.getId(), otherSource.getId());
        assertThat(rules.findById(legacy.getId())).isEmpty();
        assertThat(images.findById(source.getId())).isPresent();
        verify(provider, never()).getObjectMetadata(any(), any());
    }

    @ParameterizedTest
    @MethodSource("storageFailures")
    void storageFailureStillAllowsReadingAndDeletingRulesButNotAssignments(RuntimeException failure) {
        var existing = rule(source, destination);
        when(provider.getObjectMetadata(any(), any())).thenThrow(failure);

        var options = service.options(destination.getId());
        assertThat(options.canTarget()).isFalse();
        assertThat(options.eligibilityError()).contains("Close and reopen");
        assertThat(options.rules()).extracting(Rule::ruleId).contains(existing.getId());
        service.delete(existing.getId(), destination.getId());
        assertThat(rules.findById(existing.getId())).isEmpty();
        assertThatThrownBy(() -> service.assign(destination.getId(), assignments(new Assignment(source.getId(), null))))
                .isSameAs(failure);
        assertThat(rules.findFirstByFrom_Id(source.getId())).isEmpty();
    }

    private static List<RuntimeException> storageFailures() {
        return List.of(new ObjectStorageUnavailableException("Storage unavailable"),
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Metadata unavailable"));
    }

    @Test
    void doesNotDeleteARuleWhoseDestinationChanged() {
        var existing = rule(source, destination);
        assertThatThrownBy(() -> service.delete(existing.getId(), otherSource.getId()))
                .isInstanceOf(FirmwareRuleConflictException.class);
        assertThat(rules.findById(existing.getId())).isPresent();
    }

    @Test
    void databaseRejectsASecondDestinationForTheSameSource() {
        rule(source, otherSource);
        assertThatThrownBy(() -> rule(source, destination)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Assignments assignments(Assignment... values) { return new Assignments(List.of(values)); }

    private FirmwareUpgradeRule rule(FirmwareImage from, FirmwareImage to) {
        var rule = new FirmwareUpgradeRule();
        rule.setFrom(from);
        rule.setTo(to);
        return rules.saveAndFlush(rule);
    }

    private FirmwareImage image(String version, boolean verified) {
        var image = new FirmwareImage();
        image.setModel(model);
        image.setVersion(version);
        image.setName(version);
        image.setInstallPackage(version + ".tar");
        if (verified) {
            var upload = new FirmwareUpload();
            upload.setId(UUID.randomUUID());
            upload.setModel(model);
            upload.setVersion(version);
            upload.setFileName(version + ".tar");
            upload.setContentType("application/octet-stream");
            upload.setStorageProvider("gcp");
            upload.setStorageContainer("test-bucket");
            upload.setObjectName(model.getName() + "/" + version + "/" + version + ".tar");
            upload.setExpectedSize(9L);
            upload.setChecksumAlgorithm("CRC32C");
            upload.setExpectedChecksum("ImIEBA==");
            upload.setObservedChecksum("ImIEBA==");
            upload.setProviderObjectVersion("17");
            upload.setStatus(FirmwareUploadStatus.VERIFIED);
            upload.setCreatedBy("rules-test");
            upload.setCreatedAt(Instant.now());
            upload.setVerifiedAt(Instant.now());
            upload.setExpiresAt(Instant.now().plusSeconds(300));
            uploads.saveAndFlush(upload);
            image.setVerifiedUpload(upload);
            var location = new ObjectStorageLocation("gcp", "test-bucket", upload.getObjectName());
            when(provider.getLocation(upload.getObjectName())).thenReturn(location);
            when(provider.getObjectMetadata(location, "CRC32C")).thenReturn(Optional.of(
                    new StoredObjectMetadata(9L, new ObjectChecksum("CRC32C", "ImIEBA=="), "17")));
        }
        return images.saveAndFlush(image);
    }
}
