package us.dot.its.jpo.ode.api.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuFirmwareDeviceType;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuFirmwareFileMetadata;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuFirmwareUpload;

@SpringBootTest(properties = "firmware-upload.cleanup.enabled=false")
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
@Transactional
class ObuFirmwareUploadRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-09-03T20:00:00Z");
    private static final Instant EXPIRATION_CUTOFF = NOW.minus(Duration.ofHours(1));
    private static final Instant RETENTION_CUTOFF = NOW.minus(Duration.ofDays(30));

    @Autowired
    private ObuFirmwareUploadRepository repository;

    @Autowired
    private ManufacturerRepository manufacturerRepository;

    @Autowired
    private EntityManager entityManager;

    private Manufacturer manufacturer;

    @BeforeEach
    void setUp() {
        manufacturer = new Manufacturer();
        manufacturer.setName("OBU Upload Repository Vendor");
        manufacturer = manufacturerRepository.save(manufacturer);

        // Integration tests use Hibernate DDL rather than the production Flyway
        // migrations, so reproduce the migration's PostgreSQL-only partial indexes.
        entityManager.createNativeQuery("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_obu_firmware_uploads_active_destination
                    ON obu_firmware_uploads (storage_provider, storage_container, object_name)
                    WHERE status IN ('PENDING', 'VERIFIED')
                """).executeUpdate();
        entityManager.createNativeQuery("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_obu_firmware_uploads_active_file_name
                    ON obu_firmware_uploads (manufacturer, file_name)
                    WHERE status IN ('PENDING', 'VERIFIED')
                """).executeUpdate();
    }

    @Test
    void expiresOnlyPendingUploadsBeforeExpirationCutoff() {
        var stalePending = save("stale.tar.sig", FirmwareUploadStatus.PENDING, EXPIRATION_CUTOFF.minusSeconds(1), null);
        var recentPending = save("recent.tar.sig", FirmwareUploadStatus.PENDING, EXPIRATION_CUTOFF.plusSeconds(1), null);
        var staleVerified = save("verified.tar.sig", FirmwareUploadStatus.VERIFIED,
                EXPIRATION_CUTOFF.minusSeconds(1), NOW.minusSeconds(1));

        int expired = repository.expirePendingUploads(FirmwareUploadStatus.PENDING, FirmwareUploadStatus.EXPIRED,
                EXPIRATION_CUTOFF, NOW, "SIGNED_URL_EXPIRED");
        entityManager.clear();

        assertThat(expired).isEqualTo(1);
        var expiredUpload = repository.findById(stalePending.getId()).orElseThrow();
        assertThat(expiredUpload.getStatus()).isEqualTo(FirmwareUploadStatus.EXPIRED);
        assertThat(expiredUpload.getFailureReason()).isEqualTo("SIGNED_URL_EXPIRED");
        assertThat(expiredUpload.getFinishedAt()).isEqualTo(NOW);
        assertThat(repository.findById(recentPending.getId()).orElseThrow().getStatus())
                .isEqualTo(FirmwareUploadStatus.PENDING);
        assertThat(repository.findById(staleVerified.getId()).orElseThrow().getStatus())
                .isEqualTo(FirmwareUploadStatus.VERIFIED);
    }

    @Test
    void deletesOnlyFinishedUnsuccessfulUploadsBeforeRetentionCutoff() {
        var oldFailed = save("failed.tar.sig", FirmwareUploadStatus.FAILED, NOW, RETENTION_CUTOFF.minusSeconds(1));
        var recentFailed = save("recent-failed.tar.sig", FirmwareUploadStatus.FAILED, NOW,
                RETENTION_CUTOFF.plusSeconds(1));
        var oldVerified = save("verified.tar.sig", FirmwareUploadStatus.VERIFIED, NOW,
                RETENTION_CUTOFF.minusSeconds(1));

        int deleted = repository.deleteFinishedUploadsBefore(
                List.of(FirmwareUploadStatus.FAILED, FirmwareUploadStatus.EXPIRED), RETENTION_CUTOFF);
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(oldFailed.getId())).isEmpty();
        assertThat(repository.findById(recentFailed.getId())).isPresent();
        assertThat(repository.findById(oldVerified.getId())).isPresent();
    }

    @Test
    void rejectsSecondActiveUploadForTheSameFileName() {
        save("dup.tar.sig", FirmwareUploadStatus.PENDING, NOW, null);

        assertThatThrownBy(() -> {
            var duplicate = upload("dup.tar.sig", FirmwareUploadStatus.PENDING, NOW, null);
            duplicate.setObjectName("ota/elsewhere/dup.tar.sig");
            repository.saveAndFlush(duplicate);
        }).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_obu_firmware_uploads_active_file_name");
    }

    @Test
    void allowsRetryAfterAFailedUpload() {
        save("retry.tar.sig", FirmwareUploadStatus.FAILED, NOW, NOW);

        var retry = save("retry.tar.sig", FirmwareUploadStatus.PENDING, NOW, null);

        assertThat(repository.findById(retry.getId())).isPresent();
    }

    @Test
    void listsTheVerifiedOrNewestUploadForEachObject() {
        var olderFailed = upload("a.tar.sig", FirmwareUploadStatus.FAILED, NOW, NOW);
        olderFailed.setCreatedAt(NOW.minusSeconds(60));
        repository.saveAndFlush(olderFailed);
        var verified = save("a.tar.sig", FirmwareUploadStatus.VERIFIED, NOW, NOW);
        var newerFailed = upload("a.tar.sig", FirmwareUploadStatus.FAILED, NOW, NOW);
        newerFailed.setCreatedAt(NOW.plusSeconds(60));
        repository.saveAndFlush(newerFailed);
        var other = save("b.tar.sig", FirmwareUploadStatus.PENDING, NOW, null);

        var listing = repository.findListingUploads("gcp", "bucket");

        assertThat(listing).extracting(ObuFirmwareUpload::getId).containsExactly(verified.getId(), other.getId());
        assertThat(repository.findDestinationForUpdate("gcp", "bucket", verified.getObjectName())).hasSize(3);
        assertThat(repository.findByIdForUpdate(other.getId())).isPresent();
    }

    private ObuFirmwareUpload save(String fileName, FirmwareUploadStatus status, Instant expiresAt,
            Instant finishedAt) {
        return repository.saveAndFlush(upload(fileName, status, expiresAt, finishedAt));
    }

    private ObuFirmwareUpload upload(String fileName, FirmwareUploadStatus status, Instant expiresAt,
            Instant finishedAt) {
        var upload = new ObuFirmwareUpload();
        upload.setId(UUID.randomUUID());
        upload.setManufacturer(manufacturer);
        upload.setFileName(fileName);
        upload.setMetadata(new ObuFirmwareFileMetadata(
                ObuFirmwareDeviceType.OBU, "y20.48.2", "ob4-generic", false, true));
        upload.setContentType("application/octet-stream");
        upload.setStorageProvider("gcp");
        upload.setStorageContainer("bucket");
        upload.setObjectName("ota/OBU Upload Repository Vendor/y20.48.2/" + fileName);
        upload.setExpectedSize(1024L);
        upload.setChecksumAlgorithm("CRC32C");
        upload.setExpectedChecksum("ImIEBA==");
        upload.setStatus(status);
        upload.setCreatedBy("admin");
        upload.setCreatedAt(NOW);
        upload.setExpiresAt(expiresAt);
        upload.setFinishedAt(finishedAt);
        return upload;
    }
}
