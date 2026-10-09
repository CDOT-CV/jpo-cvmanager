package us.dot.its.jpo.ode.api.services;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "firmware-upload.cleanup")
public class FirmwareUploadCleanupProperties {
    private boolean enabled = true;
    private Duration interval = Duration.ofHours(1);
    private Duration expirationGrace = Duration.ofHours(1);
    private Duration retention = Duration.ofDays(30);
}
