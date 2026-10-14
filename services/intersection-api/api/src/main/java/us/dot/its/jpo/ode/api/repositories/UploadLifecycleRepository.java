package us.dot.its.jpo.ode.api.repositories;

import java.time.Instant;
import java.util.Collection;

import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;

/**
 * Cleanup operations for an upload table. Each repository supplies its own
 * queries; the cleanup task applies them to every upload table.
 */
public interface UploadLifecycleRepository {
    int expirePendingUploads(FirmwareUploadStatus pendingStatus, FirmwareUploadStatus expiredStatus,
            Instant expirationCutoff, Instant finishedAt, String failureReason);

    int deleteFinishedUploadsBefore(Collection<FirmwareUploadStatus> statuses, Instant retentionCutoff);
}
