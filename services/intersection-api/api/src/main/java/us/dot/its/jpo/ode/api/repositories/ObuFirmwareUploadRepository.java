package us.dot.its.jpo.ode.api.repositories;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuFirmwareUpload;

@Repository
public interface ObuFirmwareUploadRepository
        extends JpaRepository<ObuFirmwareUpload, UUID>, UploadLifecycleRepository {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select upload from ObuFirmwareUpload upload where upload.id = :id")
    Optional<ObuFirmwareUpload> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select upload from ObuFirmwareUpload upload
            where upload.storageProvider = :provider and upload.storageContainer = :container
              and upload.objectName = :name order by upload.id
            """)
    List<ObuFirmwareUpload> findDestinationForUpdate(@Param("provider") String provider,
            @Param("container") String container, @Param("name") String name);

    // Prefer verified evidence for each object, falling back to its newest upload
    // attempt when no verified record exists
    @Query(value = """
            select distinct on (object_name) * from obu_firmware_uploads
            where storage_provider = :provider and storage_container = :container
            order by object_name, (status = 'VERIFIED') desc, created_at desc, upload_id
            """, nativeQuery = true)
    List<ObuFirmwareUpload> findListingUploads(@Param("provider") String provider,
            @Param("container") String container);

    @Override
    @Modifying
    @Transactional
    @Query("""
            update ObuFirmwareUpload upload
            set upload.status = :expiredStatus,
                upload.finishedAt = :finishedAt,
                upload.failureReason = :failureReason
            where upload.status = :pendingStatus
              and upload.expiresAt < :expirationCutoff
            """)
    int expirePendingUploads(
            @Param("pendingStatus") FirmwareUploadStatus pendingStatus,
            @Param("expiredStatus") FirmwareUploadStatus expiredStatus,
            @Param("expirationCutoff") Instant expirationCutoff,
            @Param("finishedAt") Instant finishedAt,
            @Param("failureReason") String failureReason);

    @Override
    @Modifying
    @Transactional
    @Query("""
            delete from ObuFirmwareUpload upload
            where upload.status in :statuses
              and upload.finishedAt < :retentionCutoff
            """)
    int deleteFinishedUploadsBefore(
            @Param("statuses") Collection<FirmwareUploadStatus> statuses,
            @Param("retentionCutoff") Instant retentionCutoff);
}
