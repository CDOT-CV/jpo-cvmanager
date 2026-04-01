package us.dot.its.jpo.ode.api.models.scms;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "A single scms health record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScmsHealthDto {
    private String health;

    @Schema(description = "The expiration time of the scms health record", example = "01/01/2020 12:00:00 AM")
    private String expiration;
}