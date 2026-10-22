package us.dot.its.jpo.snmp.client;

import java.time.Duration;

/**
 * Request policy used by a task-scoped client. SET always uses zero protocol retries.
 * Rediscovery may add one read attempt within the same task/operation deadline.
 *
 * @param requestTimeout timeout per protocol attempt, from one millisecond to one day
 * @param readRetries protocol retransmissions for reads, 0-10
 * @param rediscoverOnTimeout whether a timed-out known peer can be retried with fresh engine discovery
 */
public record SnmpRequestPolicy(Duration requestTimeout, int readRetries, boolean rediscoverOnTimeout) {
    /**
     * Validates timeout and retry limits.
     * @throws IllegalArgumentException if the timeout or retry count is invalid
     */
    public SnmpRequestPolicy {
        if (requestTimeout == null || requestTimeout.toMillis() < 1 || requestTimeout.compareTo(Duration.ofDays(1)) > 0
                || readRetries < 0 || readRetries > 10) throw new IllegalArgumentException("Invalid SNMP request policy");
    }

    /**
     * Returns a five-second timeout, one read retry and enabled timeout rediscovery.
     * @return default request policy
     */
    public static SnmpRequestPolicy defaults() { return new SnmpRequestPolicy(Duration.ofSeconds(5), 1, true); }
}
