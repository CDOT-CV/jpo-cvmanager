package us.dot.its.jpo.ode.api.models.devices.management;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RsuUpgradeRequest {
    private String command;

    @NotEmpty
    @JsonProperty("rsu_ip")
    private List<String> rsuIp;

    private Map<String, Object> args;
}
