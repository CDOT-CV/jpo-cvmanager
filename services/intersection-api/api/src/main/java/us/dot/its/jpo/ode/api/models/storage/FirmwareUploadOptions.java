package us.dot.its.jpo.ode.api.models.storage;

import java.util.List;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FirmwareUploadOptions(List<ManufacturerOption> manufacturers) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ManufacturerOption(
            Integer manufacturerId, String name, String fileExtension, List<ModelOption> models) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ModelOption(Integer modelId, String name) {}
}
