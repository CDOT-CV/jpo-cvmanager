package us.dot.its.jpo.ode.api.storage;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "object-storage")
public class ObjectStorageProperties {
    private Duration signedUrlExpiration = Duration.ofMinutes(15);
    private DataSize maxUploadSize = DataSize.ofGigabytes(1);
    private GcpProperties gcp = new GcpProperties();

    @Data
    public static class GcpProperties {
        private String bucketName;
        private String signingServiceAccount;
    }
}
