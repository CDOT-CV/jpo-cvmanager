package us.dot.its.jpo.ode.api.models.rsu;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "A single SNMP message forwarding configuration stored for an RSU")
public class MsgFwdConfigDto {
    @JsonProperty("Message Type")
    @Schema(description = "Forwarded message type, upper-cased", example = "BSM")
    private final String messageType;

    @JsonProperty("IP")
    @Schema(description = "Destination IPv4 address", example = "10.0.0.1")
    private final String ip;

    @JsonProperty("Port")
    @Schema(description = "Destination port", example = "46800")
    private final Integer port;

    @JsonProperty("Start DateTime")
    @Schema(description = "ISO-8601 start of the forwarding window in the configured timezone",
            example = "2026-04-10T07:28:01-06:00")
    private final String startDateTime;

    @JsonProperty("End DateTime")
    @Schema(description = "ISO-8601 end of the forwarding window in the configured timezone",
            example = "2036-04-10T07:28:01-06:00")
    private final String endDateTime;

    @JsonProperty("Config Active")
    @Schema(allowableValues = { "Enabled", "Disabled" })
    private final String configActive;

    @JsonProperty("Full WSMP")
    @Schema(description = "Whether the full WSMP payload (including security headers) is forwarded",
            allowableValues = { "Enabled", "Disabled" })
    private final String fullWsmp;
}
