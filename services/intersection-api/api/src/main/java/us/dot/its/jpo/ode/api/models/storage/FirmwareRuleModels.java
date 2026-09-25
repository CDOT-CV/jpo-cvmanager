package us.dot.its.jpo.ode.api.models.storage;

import java.util.List;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class FirmwareRuleModels {
    private FirmwareRuleModels() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Image(Integer firmwareId, String manufacturer, String model, String version, boolean legacy) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Rule(Integer ruleId, Image source, Image destination, boolean legacyDestination) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Options(Image destination, boolean canTarget, String eligibilityError,
            List<Image> sources, List<Rule> rules) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Assignment(@NotNull @Positive Integer sourceId, @Positive Integer expectedTargetId) {}

    // The previous destination is part of the request so reassignment requires an
    // explicit choice based on the rules the administrator actually saw.
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Assignments(@NotEmpty @Size(max = 1000) List<@NotNull @Valid Assignment> sources) {}
}
