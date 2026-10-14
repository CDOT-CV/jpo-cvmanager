package us.dot.its.jpo.ode.api.models.postgres.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
