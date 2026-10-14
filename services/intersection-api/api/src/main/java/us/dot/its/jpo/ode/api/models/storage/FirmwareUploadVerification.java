package us.dot.its.jpo.ode.api.models.storage;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;

@Schema(description = "Verified state of a firmware artifact upload")
public record FirmwareUploadVerification(
        @JsonProperty("upload_id") UUID uploadId,
        FirmwareUploadStatus status,
        @JsonProperty("object_name") String objectName,
        @JsonProperty("content_length") Long contentLength,
        @JsonProperty("checksum_algorithm") String checksumAlgorithm,
        String checksum,
        @JsonProperty("provider_object_version") String providerObjectVersion,
        @JsonProperty("verified_at") Instant verifiedAt) {
}
