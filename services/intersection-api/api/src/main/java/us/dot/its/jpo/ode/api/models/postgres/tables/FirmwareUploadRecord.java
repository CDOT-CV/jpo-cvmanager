package us.dot.its.jpo.ode.api.models.postgres.tables;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Columns shared by every firmware upload table: the uploaded file, its storage
 * destination, the upload intent, and the verification evidence.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class FirmwareUploadRecord implements VerifiableUpload {
    @Id
    @Column(name = "upload_id", nullable = false)
    private UUID id;

    @Size(max = 128)
    @NotNull
    @Column(name = "file_name", nullable = false, length = 128)
    private String fileName;

    @Size(max = 255)
    @NotNull
    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Size(max = 32)
    @NotNull
    @Column(name = "storage_provider", nullable = false, length = 32)
    private String storageProvider;

    @Size(max = 255)
    @NotNull
    @Column(name = "storage_container", nullable = false, length = 255)
    private String storageContainer;

    @NotNull
    @Column(name = "object_name", nullable = false, columnDefinition = "text")
    private String objectName;

    @NotNull
    @Column(name = "expected_size", nullable = false)
    private Long expectedSize;

    @Size(max = 32)
    @NotNull
    @Column(name = "checksum_algorithm", nullable = false, length = 32)
    private String checksumAlgorithm;

    @Size(max = 128)
    @NotNull
    @Column(name = "expected_checksum", nullable = false, length = 128)
    private String expectedChecksum;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private FirmwareUploadStatus status;

    @Size(max = 255)
    @NotNull
    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Size(max = 255)
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "provider_object_version", columnDefinition = "text")
    private String providerObjectVersion;

    @Size(max = 128)
    @Column(name = "observed_checksum", length = 128)
    private String observedChecksum;
}
