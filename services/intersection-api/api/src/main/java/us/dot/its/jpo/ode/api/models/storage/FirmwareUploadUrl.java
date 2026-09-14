package us.dot.its.jpo.ode.api.models.storage;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tracked, short-lived instructions for uploading one firmware artifact")
public record FirmwareUploadUrl(
        @JsonProperty("upload_id") UUID uploadId,
        @JsonProperty("upload_url") String uploadUrl,
        String method,
        @JsonProperty("object_name") String objectName,
        @JsonProperty("expires_at") Instant expiresAt,
        @JsonProperty("required_headers") Map<String, String> requiredHeaders) {
}
