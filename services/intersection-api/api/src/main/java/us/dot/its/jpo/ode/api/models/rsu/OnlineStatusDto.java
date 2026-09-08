package us.dot.its.jpo.ode.api.models.rsu;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "The current rolling online status of an RSU")
public class OnlineStatusDto {
    @JsonProperty("current_status")
    @Schema(allowableValues = { "online", "offline", "unstable" })
    private final String currentStatus;
}
