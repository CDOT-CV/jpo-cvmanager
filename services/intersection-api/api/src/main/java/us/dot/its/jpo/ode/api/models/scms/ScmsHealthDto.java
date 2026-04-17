package us.dot.its.jpo.ode.api.models.scms;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "A single SCMS health record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScmsHealthDto {
    @Schema(description = "Whether SCMS certificates associated with an RSU are up-to-date (true) or out-of-date (false)", example = "true")
    private Boolean health;

    @Schema(description = "The expiration time of the SCMS certificates associated with an RSU", example = "04/10/2026 01:28:01 PM")
    private String expiration;
}