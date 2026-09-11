package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.fixtures.TestFixtures;
import us.dot.its.jpo.ode.api.models.postgres.tables.*;
import us.dot.its.jpo.ode.api.models.storage.ObjectChecksum;
import us.dot.its.jpo.ode.api.models.storage.StoredObjectMetadata;
import us.dot.its.jpo.ode.api.repositories.*;
import us.dot.its.jpo.ode.api.services.FirmwareDeletionService.FirmwareDeletionConflictException;
import us.dot.its.jpo.ode.api.storage.GcpStorageClientProvider;
import us.dot.its.jpo.ode.api.storage.ObjectStorageService.ObjectStorageConflictException;
import us.dot.its.jpo.ode.api.storage.ObjectStorageUnavailableException;

@SpringBootTest(properties = {"firmware-upload.cleanup.enabled=false", "object-storage.provider=gcp",
        "object-storage.gcp.bucket-name=deletion-test"})
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
class FirmwareDeletionServiceTest {
    @Autowired private FirmwareDeletionService deletion;
    @Autowired private FirmwareRegistrationService registration;
    @Autowired private FirmwareUploadRepository uploads;
    @Autowired private FirmwareImageRepository images;
    @Autowired private FirmwareUpgradeRuleRepository rules;
    @Autowired private ManufacturerRepository manufacturers;
    @Autowired private RsuModelRepository models;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private GcpStorageClientProvider clientProvider;

    private Storage cloud;
    private Manufacturer manufacturer;
    private RsuModel model;
    private String objectName;
    private Organization organization;
    private SnmpProtocol protocol;
    private final StoredObjectMetadata metadata = new StoredObjectMetadata(
            9L, new ObjectChecksum("CRC32C", "ImIEBA=="), "17");

    @BeforeEach
    void setUp() throws Exception {
        cloud = mock(Storage.class);
        when(clientProvider.getStorage()).thenReturn(cloud);
        when(cloud.delete(any(BlobId.class), any(Storage.BlobSourceOption[].class))).thenReturn(true);
        manufacturer = new Manufacturer();
        manufacturer.setName("Deletion-" + UUID.randomUUID());
        manufacturer = manufacturers.save(manufacturer);
        model = new RsuModel();
        model.setName("Model-" + UUID.randomUUID());
        model.setManufacturer(manufacturer);
        model.setSupportedRadio("C-V2X");
        model = models.save(model);
        objectName = manufacturer.getName() + "/" + model.getName() + "/v1/v1.tar";
    }

    @AfterEach
    void cleanUp() {
        // Fixtures are committed so these tests exercise the service's real commit
        // and rollback boundaries. Remove only this test's model and dependencies.
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("delete from max_retry_limit_reached_instances where rsu_id in (select rsu_id from rsus where model = ?)", model.getId());
            jdbc.update("delete from rsus where model = ?", model.getId());
            jdbc.update("delete from firmware_upgrade_rules where from_id in (select firmware_id from firmware_images where model = ?) or to_id in (select firmware_id from firmware_images where model = ?)", model.getId(), model.getId());
            jdbc.update("delete from firmware_images where model = ?", model.getId());
            jdbc.update("delete from firmware_uploads where model = ?", model.getId());
            models.deleteById(model.getId());
            manufacturers.deleteById(manufacturer.getId());
            if (organization != null) {
                entityManager.createQuery("delete from RsuCredential credential where credential.ownerOrganization.id = :id")
                        .setParameter("id", organization.getId()).executeUpdate();
                entityManager.createQuery("delete from SnmpCredential credential where credential.ownerOrganization.id = :id")
                        .setParameter("id", organization.getId()).executeUpdate();
                entityManager.remove(entityManager.find(Organization.class, organization.getId()));
                entityManager.remove(entityManager.find(SnmpProtocol.class, protocol.getId()));
            }
        });
    }

    @Test
    void deletesVerifiedImageRulesAndAllAttemptsAndAllowsRegisteringTheVersionAgain() {
        var upload = saveUpload(FirmwareUploadStatus.VERIFIED);
        var failedAttempt = saveUpload(FirmwareUploadStatus.FAILED);
        var image = saveImage(upload, "v1");
        var otherImage = saveImage(null, "v0");
        var rule = new FirmwareUpgradeRule();
        rule.setFrom(otherImage);
        rule.setTo(image);
        rule = rules.save(rule);

        deletion.delete(objectId(), "17");

        assertThat(uploads.findById(upload.getId())).isEmpty();
        assertThat(uploads.findById(failedAttempt.getId())).isEmpty();
        assertThat(images.findById(image.getId())).isEmpty();
        assertThat(rules.findById(rule.getId())).isEmpty();
        assertThat(images.findById(otherImage.getId())).isPresent();
        verify(cloud).delete(BlobId.of("deletion-test", objectName), Storage.BlobSourceOption.generationMatch(17L));

        var retry = saveUpload(FirmwareUploadStatus.PENDING);
        registration.register(retry.getId(), metadata);
        assertThat(images.findByModelIdAndVersion(model.getId(), "v1")).isPresent();
    }

    @ParameterizedTest
    @EnumSource(value = FirmwareUploadStatus.class, names = {"PENDING", "FAILED", "EXPIRED"})
    void deletesUnverifiedUploads(FirmwareUploadStatus state) {
        var upload = saveUpload(state);
        deletion.delete(objectId(), "17");
        assertThat(uploads.findById(upload.getId())).isEmpty();
        verify(cloud).delete(BlobId.of("deletion-test", objectName), Storage.BlobSourceOption.generationMatch(17L));
    }

    @Test
    void deletesUntrackedObjectsAndRetriesWhenAlreadyAbsent() {
        when(cloud.delete(any(BlobId.class), any(Storage.BlobSourceOption[].class))).thenReturn(true, false);
        deletion.delete(objectId(), "17");
        deletion.delete(objectId(), "17");
        verify(cloud, times(2)).delete(BlobId.of("deletion-test", objectName), Storage.BlobSourceOption.generationMatch(17L));
    }

    @Test
    void deletesChangedObjectUsingTheVersionTheAdminSelected() {
        var upload = saveUpload(FirmwareUploadStatus.VERIFIED);
        var image = saveImage(upload, "v1");

        deletion.delete(objectId(), "18");

        verify(cloud).delete(BlobId.of("deletion-test", objectName), Storage.BlobSourceOption.generationMatch(18L));
        assertThat(images.findById(image.getId())).isEmpty();
        assertThat(uploads.findById(upload.getId())).isEmpty();
    }

    @Test
    void deletesLegacyImageButPreservesAttemptsForOtherContainers() {
        var image = saveImage(null, "v1");
        var elsewhere = saveUpload(FirmwareUploadStatus.PENDING);
        elsewhere.setStorageContainer("another-bucket");
        elsewhere.setExpiresAt(Instant.now().plusSeconds(900));
        uploads.saveAndFlush(elsewhere);

        deletion.delete(objectId(), "17");

        assertThat(images.findById(image.getId())).isEmpty();
        assertThat(uploads.findById(elsewhere.getId())).isPresent();
        verify(cloud).delete(BlobId.of("deletion-test", objectName), Storage.BlobSourceOption.generationMatch(17L));
    }

    @Test
    void rollsBackDatabaseCleanupOnStorageFailureAndCanRetry() {
        var upload = saveUpload(FirmwareUploadStatus.VERIFIED);
        var image = saveImage(upload, "v1");
        var rule = new FirmwareUpgradeRule();
        rule.setFrom(saveImage(null, "v0"));
        rule.setTo(image);
        rule = rules.save(rule);
        // The provider may have deleted the file before a response was lost.
        when(cloud.delete(any(BlobId.class), any(Storage.BlobSourceOption[].class)))
                .thenThrow(new StorageException(503, "response lost")).thenReturn(false);

        assertThatThrownBy(() -> deletion.delete(objectId(), "17")).isInstanceOf(ObjectStorageUnavailableException.class);
        assertThat(uploads.findById(upload.getId())).isPresent();
        assertThat(images.findById(image.getId())).isPresent();
        assertThat(rules.findById(rule.getId())).isPresent();

        deletion.delete(objectId(), "17");
        assertThat(uploads.findById(upload.getId())).isEmpty();
        assertThat(images.findById(image.getId())).isEmpty();
        assertThat(rules.findById(rule.getId())).isEmpty();
    }

    @Test
    void changedGenerationKeepsDatabaseRecords() {
        var upload = saveUpload(FirmwareUploadStatus.VERIFIED);
        var image = saveImage(upload, "v1");
        when(cloud.delete(any(BlobId.class), any(Storage.BlobSourceOption[].class)))
                .thenThrow(new StorageException(412, "changed"));
        assertThatThrownBy(() -> deletion.delete(objectId(), "17")).isInstanceOf(ObjectStorageConflictException.class);
        assertThat(uploads.findById(upload.getId())).isPresent();
        assertThat(images.findById(image.getId())).isPresent();
    }

    @ParameterizedTest
    @EnumSource(FirmwareUploadStatus.class)
    void refusesDeletionWhileAnySignedUrlRemainsValid(FirmwareUploadStatus state) {
        var upload = saveUpload(state);
        upload.setExpiresAt(Instant.now().plusSeconds(900));
        uploads.saveAndFlush(upload);
        assertThatThrownBy(() -> deletion.delete(objectId(), "17"))
                .isInstanceOf(FirmwareDeletionConflictException.class).hasMessageContaining("still valid");
        assertThat(uploads.findById(upload.getId())).isPresent();
        verifyNoInteractions(cloud);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsCurrentAndTargetRsuReferencesIncludingLegacyImages(boolean target) throws Exception {
        var image = saveImage(null, "v1");
        var rsu = createRsu();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var managed = entityManager.find(Rsu.class, rsu.getId());
            if (target) managed.setTargetFirmwareVersion(image);
            else managed.setFirmwareVersion(image);
        });
        assertThatThrownBy(() -> deletion.delete(objectId(), "17"))
                .isInstanceOf(FirmwareDeletionConflictException.class).hasMessageContaining("current or target");
        assertThat(images.findById(image.getId())).isPresent();
        verifyNoInteractions(cloud);
    }

    @Test
    void rejectsFailureHistoryReferences() throws Exception {
        var image = saveImage(null, "v1");
        var rsu = createRsu();
        jdbc.update("insert into max_retry_limit_reached_instances (rsu_id, reached_at, target_firmware_version) values (?, now(), ?)",
                rsu.getId(), image.getId());
        assertThatThrownBy(() -> deletion.delete(objectId(), "17"))
                .isInstanceOf(FirmwareDeletionConflictException.class).hasMessageContaining("failure history");
        verifyNoInteractions(cloud);
    }

    @Test
    void concurrentCompletionCannotRestoreDeletedRecords() throws Exception {
        var upload = saveUpload(FirmwareUploadStatus.PENDING);
        var deleting = new CountDownLatch(1);
        var finishDelete = new CountDownLatch(1);
        when(cloud.delete(any(BlobId.class), any(Storage.BlobSourceOption[].class))).thenAnswer(invocation -> {
            deleting.countDown();
            if (!finishDelete.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
            return true;
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var delete = executor.submit(() -> deletion.delete(objectId(), "17"));
            try {
                assertThat(deleting.await(10, TimeUnit.SECONDS)).isTrue();
                var complete = executor.submit(() -> registration.register(upload.getId(), metadata));
                finishDelete.countDown();
                delete.get(10, TimeUnit.SECONDS);
                assertThatThrownBy(() -> complete.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(EntityNotFoundException.class);
            } finally {
                finishDelete.countDown();
            }
        }
        registration.markFailed(upload.getId(), "CHECKSUM_MISMATCH");
        assertThat(uploads.findById(upload.getId())).isEmpty();
        assertThat(images.findByModelIdAndVersion(model.getId(), "v1")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"!invalid", "Z2Nw", "gcp\nota/update.tar", "gcp\nOTA/update.tar", "gcp\nfolder/", "other\nfile.tar"})
    void rejectsMalformedReservedOrOtherProviderIdentifiers(String value) {
        String id = value.startsWith("!") || value.equals("Z2Nw") ? value : encode(value);
        assertThatThrownBy(() -> deletion.delete(id, "17"))
                .isInstanceOfAny(IllegalArgumentException.class, FirmwareDeletionConflictException.class);
        verifyNoInteractions(cloud);
    }

    private FirmwareUpload saveUpload(FirmwareUploadStatus state) {
        var upload = new FirmwareUpload();
        upload.setId(UUID.randomUUID());
        upload.setModel(model);
        upload.setVersion("v1");
        upload.setFileName("v1.tar");
        upload.setContentType("application/octet-stream");
        upload.setStorageProvider("gcp");
        upload.setStorageContainer("deletion-test");
        upload.setObjectName(objectName);
        upload.setExpectedSize(9L);
        upload.setChecksumAlgorithm("CRC32C");
        upload.setExpectedChecksum("ImIEBA==");
        upload.setStatus(state);
        upload.setCreatedBy("test");
        upload.setCreatedAt(Instant.now().minusSeconds(7200));
        upload.setExpiresAt(Instant.now().minusSeconds(3600));
        if (state == FirmwareUploadStatus.VERIFIED) {
            upload.setVerifiedAt(Instant.now().minusSeconds(3601));
            upload.setObservedChecksum("ImIEBA==");
            upload.setProviderObjectVersion("17");
        }
        return uploads.saveAndFlush(upload);
    }

    private FirmwareImage saveImage(FirmwareUpload upload, String version) {
        var image = new FirmwareImage();
        image.setName(version);
        image.setModel(model);
        image.setVersion(version);
        image.setInstallPackage(version + ".tar");
        image.setVerifiedUpload(upload);
        return images.saveAndFlush(image);
    }

    private Rsu createRsu() throws Exception {
        var fixtures = new TestFixtures();
        organization = fixtures.createOrg(null, "Deletion-" + UUID.randomUUID(), "test@example.com");
        var credential = fixtures.createRandomRsuCredential(organization);
        var snmpCredential = fixtures.createRandomSnmpCredential(organization);
        protocol = fixtures.createRandomSnmpProtocol();
        protocol.setNickname("Deletion-" + UUID.randomUUID());
        var rsu = fixtures.createRsu("192.0.2.199", model, credential, snmpCredential, protocol);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            entityManager.persist(organization);
            entityManager.persist(credential);
            entityManager.persist(snmpCredential);
            entityManager.persist(protocol);
            entityManager.persist(rsu);
        });
        return rsu;
    }

    private String objectId() {
        return encode("gcp\n" + objectName);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
