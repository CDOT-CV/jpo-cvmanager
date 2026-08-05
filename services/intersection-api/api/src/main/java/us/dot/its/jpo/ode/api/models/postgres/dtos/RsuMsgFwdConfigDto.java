package us.dot.its.jpo.ode.api.models.postgres.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single SNMP message-forwarding configuration entry.
 * Mirrors the entry shape produced by the Python
 * rsu_message_forward_helpers.format_snmp_msgfwd_configs function.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RsuMsgFwdConfigDto {
    @JsonProperty("Message Type")
    private String messageType;
    @JsonProperty("IP")
    private String ip;
    @JsonProperty("Port")
    private Integer port;
    @JsonProperty("Start DateTime")
    private String startDateTime;
    @JsonProperty("End DateTime")
    private String endDateTime;
    @JsonProperty("Config Active")
    private String configActive;
    @JsonProperty("Full WSMP")
    private String fullWsmp;
}
