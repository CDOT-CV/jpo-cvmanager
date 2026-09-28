package us.dot.its.jpo.ode.api.models.storage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FirmwareObjectPage(String provider, List<Item> objects, long totalElements) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Item(String objectId, String objectName, String manufacturer, String model,
            String version, String fileName, Long contentLength,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt,
            String providerObjectVersion, UUID uploadId, Integer firmwareId, String uploadStatus,
            String verificationStatus) {}
}
