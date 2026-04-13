package us.dot.its.jpo.ode.api.models.emails.contents;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Contents of firmware upgrade failure email, including RSU IP address, error message, failure type denoted by vendor, and stack trace of the exception that occurred")
@Data
public class FirmwareUpgradeFailureEmailContents {
    @Schema(description = "IP address of the RSU that experienced the firmware upgrade failure")
    @JsonProperty("rsu_ip")
    private String rsuIp;
    @Schema(description = "Error message describing the firmware upgrade failure")
    @JsonProperty("message")
    private String message;
    @Schema(description = "Type of firmware upgrade failure, denoted by vendor", example = "Yunex Firmware Upgrade Error")
    @JsonProperty("failure_type")
    private String failureType;
    @Schema(description = "Stack trace of the exception that occurred")
    @JsonProperty("stack_trace")
    private String stackTrace;
}
