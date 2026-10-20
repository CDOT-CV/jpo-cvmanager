package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareImage;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpload;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;
import us.dot.its.jpo.ode.api.models.storage.FirmwareRuleModels.Assignment;
import us.dot.its.jpo.ode.api.models.storage.FirmwareRuleModels.Assignments;
import us.dot.its.jpo.ode.api.models.storage.ObjectChecksum;
import us.dot.its.jpo.ode.api.models.storage.ObjectStorageLocation;
import us.dot.its.jpo.ode.api.models.storage.StoredObjectMetadata;
import us.dot.its.jpo.ode.api.repositories.FirmwareImageRepository;
import us.dot.its.jpo.ode.api.repositories.FirmwareUpgradeRuleRepository;
import us.dot.its.jpo.ode.api.repositories.FirmwareUploadRepository;
import us.dot.its.jpo.ode.api.repositories.ManufacturerRepository;
import us.dot.its.jpo.ode.api.repositories.RsuModelRepository;
import us.dot.its.jpo.ode.api.services.FirmwareRuleService.FirmwareRuleConflictException;
import us.dot.its.jpo.ode.api.storage.ObjectStorageService;
import us.dot.its.jpo.ode.api.storage.ObjectStorageServiceRegistry;

@SpringBootTest(properties = "firmware-upload.cleanup.enabled=false")
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
class FirmwareRuleTransactionTest {
    @Autowired private FirmwareRuleService service;
    @Autowired private FirmwareDeletionService deletion;
    @Autowired private FirmwareImageRepository images;
    @Autowired private FirmwareUploadRepository uploads;
    @Autowired private ManufacturerRepository manufacturers;
    @Autowired private RsuModelRepository models;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private FirmwareUpgradeRuleRepository rules;
    @MockitoBean private ObjectStorageServiceRegistry storage;

    private ObjectStorageService provider;
    private Manufacturer manufacturer;
    private RsuModel model;
    private FirmwareImage source;
    private FirmwareImage otherSource;
    private FirmwareImage destination;
    private String objectName;
    private final StoredObjectMetadata metadata = new StoredObjectMetadata(
            9L, new ObjectChecksum("CRC32C", "ImIEBA=="), "17");

    @BeforeEach
    void setUp() {
        provider = mock(ObjectStorageService.class);
        when(storage.getActiveService()).thenReturn(provider);
        when(provider.providerName()).thenReturn("gcp");
        manufacturer = new Manufacturer();
        manufacturer.setName("RuleTransactions-" + UUID.randomUUID());
        manufacturers.saveAndFlush(manufacturer);
        model = new RsuModel();
        model.setName("Model-" + UUID.randomUUID());
        model.setSupportedRadio("C-V2X");
        model.setManufacturer(manufacturer);
        models.saveAndFlush(model);
        source = image("v1");
        otherSource = image("v2");
        destination = image("v3");
        objectName = manufacturer.getName() + "/" + model.getName() + "/v3/v3.tar";

        var upload = new FirmwareUpload();
        upload.setId(UUID.randomUUID());
        upload.setModel(model);
        upload.setVersion("v3");
        upload.setFileName("v3.tar");
        upload.setContentType("application/octet-stream");
        upload.setStorageProvider("gcp");
        upload.setStorageContainer("rule-test");
        upload.setObjectName(objectName);
        upload.setExpectedSize(9L);
        upload.setChecksumAlgorithm("CRC32C");
        upload.setExpectedChecksum("ImIEBA==");
        upload.setObservedChecksum("ImIEBA==");
        upload.setProviderObjectVersion("17");
        upload.setStatus(FirmwareUploadStatus.VERIFIED);
        upload.setCreatedBy("rule-test");
        upload.setCreatedAt(Instant.now().minusSeconds(600));
        upload.setExpiresAt(Instant.now().minusSeconds(300));
        upload.setVerifiedAt(Instant.now());
        uploads.saveAndFlush(upload);
        destination.setVerifiedUpload(upload);
        images.saveAndFlush(destination);
        when(provider.getLocation(objectName)).thenReturn(new ObjectStorageLocation("gcp", "rule-test", objectName));
        when(provider.getObjectMetadata(any(), any())).thenReturn(Optional.of(metadata));
    }

    @AfterEach
    void cleanUp() {
        // Fixtures commit independently so the tests exercise the service's own
        // transaction boundary. Cleanup is restricted to this test's model.
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("delete from firmware_upgrade_rules where from_id in (select firmware_id from firmware_images where model = ?) or to_id in (select firmware_id from firmware_images where model = ?)", model.getId(), model.getId());
            jdbc.update("delete from firmware_images where model = ?", model.getId());
            jdbc.update("delete from firmware_uploads where model = ?", model.getId());
            models.deleteById(model.getId());
            manufacturers.deleteById(manufacturer.getId());
        });
    }

    @Test
    void commitsAssignmentsWithoutATestManagedTransaction() {
        service.assign(destination.getId(), assignments(source, otherSource));
        assertThat(persistedSources()).containsExactlyInAnyOrder(source.getId(), otherSource.getId());
    }

    @Test
    void rollsBackTheFirstWriteWhenTheSecondWriteFails() {
        doAnswer(invocation -> {
            // Force the first insert to reach PostgreSQL before failing the batch.
            rules.flush();
            assertThat(jdbc.queryForObject("select count(*) from firmware_upgrade_rules where from_id = ?",
                    Integer.class, source.getId())).isEqualTo(1);
            throw new DataIntegrityViolationException("Simulated second-write failure");
        }).when(rules).save(argThat(rule -> rule != null && rule.getFrom().getId().equals(otherSource.getId())));

        assertThatThrownBy(() -> service.assign(destination.getId(), assignments(source, otherSource)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(persistedSources()).isEmpty();
    }

    @Test
    void serializesCompetingAssignmentsAndRejectsTheStaleSelection() throws Exception {
        whileAssignmentHoldsLocks(() -> service.assign(destination.getId(), assignments(source)));
        assertThat(persistedSources()).containsExactly(source.getId());
        assertThatThrownBy(() -> service.assign(destination.getId(), assignments(source)))
                .isInstanceOf(FirmwareRuleConflictException.class).hasMessageContaining("changed");
    }

    @Test
    void deletionWaitsForAssignmentAndThenRemovesItsCommittedRule() throws Exception {
        String objectId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("gcp\n" + objectName).getBytes(StandardCharsets.UTF_8));
        whileAssignmentHoldsLocks(() -> deletion.delete(objectId, "17"));
        assertThat(persistedSources()).containsExactly(source.getId());
        deletion.delete(objectId, "17");
        assertThat(persistedSources()).isEmpty();
        assertThat(images.findById(destination.getId())).isEmpty();
    }

    private void whileAssignmentHoldsLocks(Runnable competingWrite) throws Exception {
        var checkingStorage = new CountDownLatch(1);
        var finishCheck = new CountDownLatch(1);
        when(provider.getObjectMetadata(any(), any())).thenAnswer(invocation -> {
            checkingStorage.countDown();
            if (!finishCheck.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test");
            return Optional.of(metadata);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.assign(destination.getId(), assignments(source)));
            try {
                assertThat(checkingStorage.await(10, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    // A database lock timeout proves the second writer was blocked,
                    // rather than relying on thread timing or sleeps.
                    jdbc.execute("set local lock_timeout = '1s'");
                    competingWrite.run();
                }));
                assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(PessimisticLockingFailureException.class);
            } finally {
                finishCheck.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
        }
    }

    private List<Integer> persistedSources() {
        return new TransactionTemplate(transactions).execute(status -> jdbc.queryForList(
                "select from_id from firmware_upgrade_rules where to_id = ?", Integer.class, destination.getId()));
    }

    private Assignments assignments(FirmwareImage... sources) {
        return new Assignments(java.util.Arrays.stream(sources).map(image -> new Assignment(image.getId(), null)).toList());
    }

    private FirmwareImage image(String version) {
        var image = new FirmwareImage();
        image.setModel(model);
        image.setVersion(version);
        image.setName(version);
        image.setInstallPackage(version + ".tar");
        return images.saveAndFlush(image);
    }
}
