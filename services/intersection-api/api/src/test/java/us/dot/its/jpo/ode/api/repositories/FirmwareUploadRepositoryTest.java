package us.dot.its.jpo.ode.api.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpload;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;

@SpringBootTest(properties = "firmware-upload.cleanup.enabled=false")
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
@Transactional
class FirmwareUploadRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-09-03T20:00:00Z");
    private static final Instant EXPIRATION_CUTOFF = NOW.minus(Duration.ofHours(1));
    private static final Instant RETENTION_CUTOFF = NOW.minus(Duration.ofDays(30));

    @Autowired
    private FirmwareUploadRepository repository;

    @Autowired
    private ManufacturerRepository manufacturerRepository;

    @Autowired
    private RsuModelRepository rsuModelRepository;

    @Autowired
    private EntityManager entityManager;

    private RsuModel model;

    @BeforeEach
    void setUp() {
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName("Repository Test Vendor");
        manufacturer = manufacturerRepository.save(manufacturer);

        model = new RsuModel();
        model.setName("Repository Test Model");
        model.setSupportedRadio("C-V2X");
        model.setManufacturer(manufacturer);
        model = rsuModelRepository.save(model);
    }

    @Test
    void expiresOnlyPendingUploadsBeforeExpirationCutoff() {
        FirmwareUpload stalePending = saveUpload(FirmwareUploadStatus.PENDING,
                EXPIRATION_CUTOFF.minusSeconds(1), null);
        FirmwareUpload pendingAtBoundary = saveUpload(FirmwareUploadStatus.PENDING,
                EXPIRATION_CUTOFF, null);
        FirmwareUpload recentPending = saveUpload(FirmwareUploadStatus.PENDING,
                EXPIRATION_CUTOFF.plusSeconds(1), null);
        FirmwareUpload staleFailed = saveUpload(FirmwareUploadStatus.FAILED,
                EXPIRATION_CUTOFF.minusSeconds(1), NOW.minusSeconds(1));
        entityManager.flush();

        int expired = repository.expirePendingUploads(
                FirmwareUploadStatus.PENDING,
                FirmwareUploadStatus.EXPIRED,
                EXPIRATION_CUTOFF,
                NOW,
                "SIGNED_URL_EXPIRED");
        entityManager.flush();
        entityManager.clear();

        assertThat(expired).isOne();

        FirmwareUpload expiredUpload = repository.findById(stalePending.getId()).orElseThrow();
        assertThat(expiredUpload.getStatus()).isEqualTo(FirmwareUploadStatus.EXPIRED);
        assertThat(expiredUpload.getFinishedAt()).isEqualTo(NOW);
        assertThat(expiredUpload.getFailureReason()).isEqualTo("SIGNED_URL_EXPIRED");

        assertThat(repository.findById(pendingAtBoundary.getId())).hasValueSatisfying(upload ->
                assertThat(upload.getStatus()).isEqualTo(FirmwareUploadStatus.PENDING));
        assertThat(repository.findById(recentPending.getId())).hasValueSatisfying(upload ->
                assertThat(upload.getStatus()).isEqualTo(FirmwareUploadStatus.PENDING));
        assertThat(repository.findById(staleFailed.getId())).hasValueSatisfying(upload ->
                assertThat(upload.getStatus()).isEqualTo(FirmwareUploadStatus.FAILED));
    }

    @Test
    void deletesOnlyFailedAndExpiredUploadsBeforeRetentionCutoff() {
        FirmwareUpload oldFailed = saveUpload(FirmwareUploadStatus.FAILED,
                NOW, RETENTION_CUTOFF.minusSeconds(1));
        FirmwareUpload oldExpired = saveUpload(FirmwareUploadStatus.EXPIRED,
                NOW, RETENTION_CUTOFF.minusSeconds(1));
        FirmwareUpload expiredAtBoundary = saveUpload(FirmwareUploadStatus.EXPIRED,
                NOW, RETENTION_CUTOFF);
        FirmwareUpload recentFailed = saveUpload(FirmwareUploadStatus.FAILED,
                NOW, RETENTION_CUTOFF.plusSeconds(1));
        FirmwareUpload oldVerified = saveUpload(FirmwareUploadStatus.VERIFIED,
                NOW, RETENTION_CUTOFF.minusSeconds(1));
        entityManager.flush();

        int deleted = repository.deleteFinishedUploadsBefore(
                List.of(FirmwareUploadStatus.FAILED, FirmwareUploadStatus.EXPIRED),
                RETENTION_CUTOFF);
        entityManager.flush();
        entityManager.clear();

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findById(oldFailed.getId())).isEmpty();
        assertThat(repository.findById(oldExpired.getId())).isEmpty();
        assertThat(repository.findById(expiredAtBoundary.getId())).isPresent();
        assertThat(repository.findById(recentFailed.getId())).isPresent();
        assertThat(repository.findById(oldVerified.getId())).isPresent();
    }

    private FirmwareUpload saveUpload(
            FirmwareUploadStatus status, Instant expiresAt, Instant finishedAt) {
        UUID id = UUID.randomUUID();
        FirmwareUpload upload = new FirmwareUpload();
        upload.setId(id);
        upload.setModel(model);
        upload.setVersion("v1");
        upload.setFileName(id + ".bin");
        upload.setContentType("application/octet-stream");
        upload.setStorageProvider("gcp");
        upload.setStorageContainer("firmware-bucket");
        upload.setObjectName("vendor/model/v1/" + id + ".bin");
        upload.setExpectedSize(12345L);
        upload.setChecksumAlgorithm("CRC32C");
        upload.setExpectedChecksum("ImIEBA==");
        upload.setStatus(status);
        upload.setCreatedBy("repository-test");
        upload.setCreatedAt(NOW.minus(Duration.ofDays(60)));
        upload.setExpiresAt(expiresAt);
        upload.setFinishedAt(finishedAt);
        if (status == FirmwareUploadStatus.VERIFIED) {
            upload.setVerifiedAt(finishedAt);
            upload.setObservedChecksum("ImIEBA==");
        } else if (status == FirmwareUploadStatus.FAILED || status == FirmwareUploadStatus.EXPIRED) {
            upload.setFailureReason("PREVIOUS_REASON");
        }
        return repository.save(upload);
    }
}
