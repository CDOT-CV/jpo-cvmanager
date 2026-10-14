package us.dot.its.jpo.ode.api.models.postgres.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the RSU message-forwarding configuration query.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing the message forwarding configurations stored for an RSU")
public class RsuMsgFwdConfigsDto {
    @JsonProperty("RsuFwdSnmpwalk")
    @Schema(description = "For rsuDsrcFwd (RSU 4.1) configs, a map of SNMP index to configuration. "
            + "For NTCIP-1218 configs, contains 'rsuReceivedMsgTable' and 'rsuXmitMsgFwdingTable', "
            + "each a map of SNMP index to configuration.",
            example = "{\"rsuReceivedMsgTable\": {\"1\": {\"Message Type\": \"BSM\", \"IP\": \"10.0.0.1\", "
                    + "\"Port\": 46800, \"Start DateTime\": \"2026-04-10T07:28:01-06:00\", "
                    + "\"End DateTime\": \"2036-04-10T07:28:01-06:00\", \"Config Active\": \"Enabled\", "
                    + "\"Full WSMP\": \"Disabled\"}}, \"rsuXmitMsgFwdingTable\": {}}")
    private Map<String, Object> rsuFwdSnmpwalk;
}
