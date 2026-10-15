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
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadRecord;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;

/**
 * Locking and cleanup queries shared by every firmware upload table. Queries
 * name the concrete entity with {@code #{#entityName}}.
 */
@NoRepositoryBean
public interface UploadLifecycleRepository<T extends FirmwareUploadRecord> extends JpaRepository<T, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select upload from #{#entityName} upload where upload.id = :id")
    Optional<T> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select upload from #{#entityName} upload
            where upload.storageProvider = :provider and upload.storageContainer = :container
              and upload.objectName = :name order by upload.id
            """)
    List<T> findDestinationForUpdate(@Param("provider") String provider,
            @Param("container") String container, @Param("name") String name);

    @Modifying
    @Transactional
    @Query("""
            update #{#entityName} upload
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
            delete from #{#entityName} upload
            where upload.status in :statuses
              and upload.finishedAt < :retentionCutoff
            """)
    int deleteFinishedUploadsBefore(
            @Param("statuses") Collection<FirmwareUploadStatus> statuses,
            @Param("retentionCutoff") Instant retentionCutoff);
}
