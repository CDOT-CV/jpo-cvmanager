package us.dot.its.jpo.ode.api.models.emails.contents;

import lombok.Data;

@Data
public class FirmwareUpgradeEmailContents {
    private String rsuIp;
    private String message;
    private String stackTrace;
}
