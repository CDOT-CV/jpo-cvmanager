package us.dot.its.jpo.ode.api.models.devices.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Expected destination of a displayed row, checked before deleting its table/index. */
public record DeleteMessageForwardingRowRequest(
        @NotNull @JsonProperty("destination_ip") String destinationIp,
        @NotNull @Min(1024) @Max(65535) @JsonProperty("destination_port") Integer destinationPort) {
}
