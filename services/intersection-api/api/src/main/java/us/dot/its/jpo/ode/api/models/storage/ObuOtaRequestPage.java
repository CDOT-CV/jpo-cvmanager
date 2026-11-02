package us.dot.its.jpo.ode.api.models.storage;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ObuOtaRequestPage(List<Item> items, long totalElements) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Item(int requestId, String obuSn,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant requestDatetime,
            String originIp, String obuFirmwareVersion, String requestedFirmwareVersion,
            String status, String errorMessage, String manufacturer) {}
}
