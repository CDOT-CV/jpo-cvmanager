package us.dot.its.jpo.ode.api.models.postgres.tables;

import java.time.Instant;

/**
 * The upload intent and verification evidence shared by firmware upload
 * records, independent of the device type the firmware targets.
 */
public interface VerifiableUpload {
    String getStorageProvider();

    String getStorageContainer();

    String getObjectName();

    Long getExpectedSize();

    String getChecksumAlgorithm();

    String getExpectedChecksum();

    String getObservedChecksum();

    String getProviderObjectVersion();

    FirmwareUploadStatus getStatus();

    Instant getExpiresAt();
}
