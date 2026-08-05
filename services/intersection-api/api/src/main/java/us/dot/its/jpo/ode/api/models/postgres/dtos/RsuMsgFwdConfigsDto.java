package us.dot.its.jpo.ode.api.models.postgres.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the RSU message-forwarding configuration query.
 * Mirrors the Python `/rsu-msgfwd-query` response: the map's values are
 * either an {@link RsuMsgFwdConfigDto} keyed by snmp_index (DSRC type),
 * or a nested {@code Map<String, RsuMsgFwdConfigDto>} under
 * "rsuReceivedMsgTable"/"rsuXmitMsgFwdingTable".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RsuMsgFwdConfigsDto {
    @JsonProperty("RsuFwdSnmpwalk")
    private Map<String, Object> rsuFwdSnmpwalk;
}
