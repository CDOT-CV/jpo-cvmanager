package us.dot.its.jpo.ode.api.models.rsu;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Response wrapper for the RSU message-forwarding configuration query.
 */
@Getter
@AllArgsConstructor
@Schema(description = "Response containing the message forwarding configurations stored for an RSU")
public class MsgFwdConfigsResponse {

    /**
     * Mirrors the legacy {@code rsufwdsnmpwalk} response shape consumed by the webapp's SnmpwalkMenu.
     * The value type is intentionally {@code Object} because the shape depends on the RSU's protocol:
     * RSU 4.1 configs map SNMP index directly to a {@link MsgFwdConfigDto}, while NTCIP-1218 configs
     * nest {@code rsuReceivedMsgTable} and {@code rsuXmitMsgFwdingTable}, each a map of SNMP index to
     * {@link MsgFwdConfigDto}.
     */
    @JsonProperty("RsuFwdSnmpwalk")
    @Schema(description = "For rsuDsrcFwd (RSU 4.1) configs, a map of SNMP index to configuration. "
            + "For NTCIP-1218 configs, contains 'rsuReceivedMsgTable' and 'rsuXmitMsgFwdingTable', "
            + "each a map of SNMP index to configuration.",
            example = "{\"rsuReceivedMsgTable\": {\"1\": {\"Message Type\": \"BSM\", \"IP\": \"10.0.0.1\", "
                    + "\"Port\": 46800, \"Start DateTime\": \"2026-04-10T07:28:01-06:00\", "
                    + "\"End DateTime\": \"2036-04-10T07:28:01-06:00\", \"Config Active\": \"Enabled\", "
                    + "\"Full WSMP\": \"Disabled\"}}, \"rsuXmitMsgFwdingTable\": {}}")
    private final Map<String, Object> rsuFwdSnmpwalk;
}
