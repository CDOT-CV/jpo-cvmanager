package us.dot.its.jpo.snmp.execution;

import java.time.Duration;
import java.util.Objects;
import us.dot.its.jpo.snmp.client.SnmpRequestPolicy;

/**
 * Policies for one execution; queue waiting does not consume the execution budget.
 * The execution deadline covers every step, including local callbacks.
 *
 * @param queueTimeout maximum queue wait, from one millisecond to one day
 * @param executionTimeout shared running budget, from one millisecond to one day
 * @param requestPolicy per-request timeout, retries and engine recovery behavior
 * @param maxRequests adapter submission cap; zero disables it. Protocol retransmissions are not counted
 * @param maxRecordedCommands logical call-history cap, 1-65536, independent of the submission cap
 */
public record SnmpTaskOptions(Duration queueTimeout, Duration executionTimeout, SnmpRequestPolicy requestPolicy, int maxRequests, int maxRecordedCommands) {
    /** Creates options with a 1024-call history limit. */
    public SnmpTaskOptions(Duration queueTimeout, Duration executionTimeout, SnmpRequestPolicy requestPolicy, int maxRequests) {
        this(queueTimeout, executionTimeout, requestPolicy, maxRequests, 1024);
    }

    /**
     * Validates timeouts and limits.
     * @throws IllegalArgumentException if a timeout or numeric limit is invalid
     * @throws NullPointerException if the request policy is null
     */
    public SnmpTaskOptions {
        for (var duration : new Duration[]{queueTimeout, executionTimeout}) {
            if (duration == null || duration.toMillis() < 1 || duration.compareTo(Duration.ofDays(1)) > 0)
                throw new IllegalArgumentException("Invalid task timeout");
        }
        Objects.requireNonNull(requestPolicy);
        if (maxRequests < 0 || maxRecordedCommands < 1 || maxRecordedCommands > 65536) throw new IllegalArgumentException("Invalid request/history limit");
    }

    /**
     * Returns a five-minute queue limit, 60-second execution limit, default request policy,
     * no submission cap and a 1024-call history cap.
     * @return default execution policy
     */
    public static SnmpTaskOptions defaults() {
        return new SnmpTaskOptions(Duration.ofMinutes(5), Duration.ofSeconds(60), SnmpRequestPolicy.defaults(), 0);
    }
}
