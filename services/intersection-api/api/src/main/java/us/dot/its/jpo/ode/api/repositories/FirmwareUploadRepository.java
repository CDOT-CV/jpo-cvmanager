package us.dot.its.jpo.ode.api.repositories;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpload;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;

@Repository
public interface FirmwareUploadRepository extends JpaRepository<FirmwareUpload, UUID> {
    @Modifying
    @Transactional
    @Query("""
            update FirmwareUpload upload
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

    @Modifying
    @Transactional
    @Query("""
            delete from FirmwareUpload upload
            where upload.status in :statuses
              and upload.finishedAt < :retentionCutoff
            """)
    int deleteFinishedUploadsBefore(
            @Param("statuses") Collection<FirmwareUploadStatus> statuses,
            @Param("retentionCutoff") Instant retentionCutoff);
}
