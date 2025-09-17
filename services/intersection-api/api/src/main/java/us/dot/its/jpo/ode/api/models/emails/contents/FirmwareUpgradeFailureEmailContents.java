package us.dot.its.jpo.ode.api.models.emails.contents;

import lombok.Data;

@Data
public class FirmwareUpgradeFailureEmailContents {
    private String rsuIp;
    private String message;
    private String failureType;
    private String stackTrace;
}
