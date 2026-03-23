package us.dot.its.jpo.ode.api.models.devices.management;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RsuUpgradeRequest {

    @NotEmpty
    @JsonProperty("rsu_ip")
    private List<String> rsuIp;
}
