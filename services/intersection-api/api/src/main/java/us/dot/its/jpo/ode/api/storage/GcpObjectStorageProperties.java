package us.dot.its.jpo.ode.api.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "object-storage.gcp")
public class GcpObjectStorageProperties {
    private String bucketName;
    private String signingServiceAccount;
}
