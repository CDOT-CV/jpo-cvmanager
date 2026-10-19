package us.dot.its.jpo.ode.api.config;

import java.time.Duration;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import us.dot.its.jpo.snmp.client.SnmpRequestPolicy;
import us.dot.its.jpo.snmp.execution.SnmpTaskOptions;

/**
 * Application limits and request policy for user-initiated SNMP tasks.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "snmp")
public class SnmpProperties {
    @Min(1)
    @Max(128)
    private int maxConcurrentTasks = 24;
    @Min(0)
    @Max(1024)
    private int maxQueuedTasks = 128;
    private Duration queueTimeout = Duration.ofSeconds(30);
    private Duration executionTimeout = Duration.ofSeconds(60);
    private Duration requestTimeout = Duration.ofSeconds(5);
    private int readRetries = 1;
    @Min(0)
    @Max(64)
    private int maxBulkRepetitions = 0;
    private boolean rediscoverOnTimeout = true;
    private String displayTimeZone = "America/Denver";

    /**
     * Builds and validates a per-task policy without changing the shared
     * transport's defaults.
     */
    public SnmpTaskOptions taskOptions() {
        return new SnmpTaskOptions(queueTimeout, executionTimeout,
                new SnmpRequestPolicy(requestTimeout, readRetries, rediscoverOnTimeout), 0);
    }
}
