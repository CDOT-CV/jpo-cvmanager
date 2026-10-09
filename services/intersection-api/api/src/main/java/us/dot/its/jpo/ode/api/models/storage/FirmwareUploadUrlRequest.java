package us.dot.its.jpo.ode.api.models.storage;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Identifies a file to upload to object storage")
public class FirmwareUploadUrlRequest {
    private static final String PATH_SEGMENT = "^[^/\\\\\\p{Cntrl}]+$";
    private static final String SAFE_FILE_COMPONENT = "^[A-Za-z0-9][A-Za-z0-9._-]*$";

    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = PATH_SEGMENT, message = "must not contain path separators or control characters")
    @JsonProperty("vendor_name")
    private String vendorName;

    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = PATH_SEGMENT, message = "must not contain path separators or control characters")
    @JsonProperty("model_name")
    private String modelName;

    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = SAFE_FILE_COMPONENT,
            message = "must start with an alphanumeric character and contain only letters, numbers, dots, underscores, or hyphens")
    private String version;

    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = SAFE_FILE_COMPONENT,
            message = "must start with an alphanumeric character and contain only letters, numbers, dots, underscores, or hyphens")
    @JsonProperty("file_name")
    private String fileName;

    @NotNull
    @Positive
    @JsonProperty("content_length")
    @Schema(description = "Exact upload size in bytes", example = "52428800")
    private Long contentLength;

    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "must contain only letters, numbers, underscores, or hyphens")
    @JsonProperty("checksum_algorithm")
    @Schema(description = "Checksum algorithm supported by the configured storage provider", example = "CRC32C")
    private String checksumAlgorithm;

    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "^[A-Za-z0-9+/=_-]+$", message = "contains invalid checksum characters")
    @JsonProperty("checksum")
    @Schema(description = "Checksum value encoded as required by checksum_algorithm", example = "ImIEBA==")
    private String checksum;

    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "^[A-Za-z0-9!#$&^_.+\\-]+/[A-Za-z0-9!#$&^_.+\\-]+$", message = "must be a valid media type")
    @JsonProperty("content_type")
    private String contentType = "application/octet-stream";
}
