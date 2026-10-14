package us.dot.its.jpo.ode.api.models.postgres.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class RsuMsgFwdConfigsDto {
    @JsonProperty("RsuFwdSnmpwalk")
    private Map<String, Object> rsuFwdSnmpwalk;
}
