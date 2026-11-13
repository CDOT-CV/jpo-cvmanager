package us.dot.its.jpo.ode.api.models.devices.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** Raw displayed destination and port, compared before deleting even a malformed row. */
public record DeleteMessageForwardingRowRequest(
        @NotNull @JsonProperty("destination_ip") String destinationIp,
        @NotNull @JsonProperty("destination_port") Integer destinationPort) {
}
