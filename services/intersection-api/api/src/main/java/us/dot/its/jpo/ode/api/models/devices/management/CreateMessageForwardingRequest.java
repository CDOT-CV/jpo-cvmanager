package us.dot.its.jpo.ode.api.models.devices.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMessageForwardingRequest extends MessageForwardingRequest {
    /** Whether forwarded messages retain IEEE 1609.2 security headers. */
    @NotNull
    @JsonProperty("include_security")
    private Boolean includeSecurity;
}
