package us.dot.its.jpo.ode.api.models.admin.intersection;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "A simple message response confirming the outcome of a write operation")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    @Schema(description = "Human-readable status message", example = "Intersection successfully modified")
    @JsonProperty("message")
    private String message;
}
