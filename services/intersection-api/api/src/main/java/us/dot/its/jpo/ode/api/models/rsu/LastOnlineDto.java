package us.dot.its.jpo.ode.api.models.rsu;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "The most recent successful ping for one RSU")
public class LastOnlineDto {
    @Schema(example = "10.0.0.1")
    private final String ip;

    @JsonProperty("last_online")
    @Schema(description = "ISO-8601 UTC timestamp of the last successful ping; null when none exists",
            example = "2026-04-10T13:28:01Z", nullable = true)
    private final Instant lastOnline;
}
