package us.dot.its.jpo.snmp.execution;

import java.time.Duration;
import java.util.Objects;
import us.dot.its.jpo.snmp.client.SnmpRequestPolicy;

/** Queue expiry is separate from the execution budget. Zero maxRequests disables the submission cap. */
public record SnmpTaskOptions(Duration queueTimeout, Duration executionTimeout, SnmpRequestPolicy requestPolicy, int maxRequests, int maxRecordedCommands) {
    public SnmpTaskOptions(Duration queueTimeout, Duration executionTimeout, SnmpRequestPolicy requestPolicy, int maxRequests) {
        this(queueTimeout, executionTimeout, requestPolicy, maxRequests, 1024);
    }
    public SnmpTaskOptions {
        for (var duration : new Duration[]{queueTimeout, executionTimeout}) {
            if (duration == null || duration.toMillis() < 1 || duration.compareTo(Duration.ofDays(1)) > 0)
                throw new IllegalArgumentException("Invalid task timeout");
        }
        Objects.requireNonNull(requestPolicy);
        if (maxRequests < 0 || maxRecordedCommands < 1 || maxRecordedCommands > 65536) throw new IllegalArgumentException("Invalid request/history limit");
    }
    public static SnmpTaskOptions defaults() {
        return new SnmpTaskOptions(Duration.ofMinutes(5), Duration.ofSeconds(60), SnmpRequestPolicy.defaults(), 0);
    }
}
