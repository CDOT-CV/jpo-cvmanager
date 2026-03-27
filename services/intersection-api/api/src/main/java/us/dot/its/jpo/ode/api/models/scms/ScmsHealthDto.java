package us.dot.its.jpo.ode.api.models.scms;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Schema(description = "A single scms health record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScmsHealthDto {
    @Schema(description = "The id of the scms health record", example = "1")
    private int id;

    @Schema(description = "The timestamp of the scms health record", example = "2020-01-01T00:00:00.000Z")
    private Instant timestamp;

    @Schema(description = "The health status of the scms", example = "true")
    private boolean health;

    @Schema(description = "The expiration time of the scms health record", example = "2020-01-01T00:00:00.000Z")
    private Instant expiration;

    @Schema(description = "The id of the rsu", example = "1")
    @JsonProperty("rsu_id")
    private int rsuId;
}