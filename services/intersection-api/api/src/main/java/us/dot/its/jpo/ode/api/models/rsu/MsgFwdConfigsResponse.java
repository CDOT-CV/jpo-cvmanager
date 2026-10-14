package us.dot.its.jpo.ode.api.models.rsu;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Response wrapper for the RSU message-forwarding configuration query.
 * The map's values are either:</p>
 * <ul>
 *   <li>a {@link MsgFwdConfigDto} keyed by snmp_index (rsuDsrcFwd configurations), or</li>
 *   <li>a nested {@code Map<String, MsgFwdConfigDto>} under "rsuReceivedMsgTable" /
 *   "rsuXmitMsgFwdingTable" (NTCIP 1218 configurations)</li>
 * </ul>
 */
@Schema(description = "SNMP message forwarding configurations stored in the database for an RSU")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MsgFwdConfigsResponse {

    @Schema(description = "Configurations keyed by snmp_index, or nested under rsuReceivedMsgTable/rsuXmitMsgFwdingTable")
    private Map<String, Object> rsuFwdSnmpwalk;
}
