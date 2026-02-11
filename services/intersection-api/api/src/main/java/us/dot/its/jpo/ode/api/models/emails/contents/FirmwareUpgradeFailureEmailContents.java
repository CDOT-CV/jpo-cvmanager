package us.dot.its.jpo.ode.api.models.emails.contents;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class FirmwareUpgradeFailureEmailContents {
    @JsonProperty("rsu_ip")
    private String rsuIp;
    @JsonProperty("message")
    private String message;
    @JsonProperty("failure_type")
    private String failureType;
    @JsonProperty("stack_trace")
    private String stackTrace;
}
