package us.dot.its.jpo.ode.api.models.devices.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import us.dot.its.jpo.snmp.rsu.ntcip1218.MessageType;

@Getter
@Setter
public class MessageForwardingRequest {
    @NotEmpty
    @Size(max = 100)
    @JsonProperty("rsu_ips")
    private List<@NotNull String> rsuIps;

    @NotNull
    @JsonProperty("message_type")
    private MessageType messageType;

    @NotNull
    @JsonProperty("destination_ip")
    private String destinationIp;
}
