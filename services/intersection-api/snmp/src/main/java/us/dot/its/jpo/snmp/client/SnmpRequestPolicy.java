package us.dot.its.jpo.snmp.client;

import java.time.Duration;

/** Applies to reads and discovery recovery. SET always has zero protocol retries. */
public record SnmpRequestPolicy(Duration requestTimeout, int readRetries, boolean rediscoverOnTimeout) {
    public SnmpRequestPolicy {
        if (requestTimeout == null || requestTimeout.toMillis() < 1 || requestTimeout.compareTo(Duration.ofDays(1)) > 0
                || readRetries < 0 || readRetries > 10) throw new IllegalArgumentException("Invalid SNMP request policy");
    }
    public static SnmpRequestPolicy defaults() { return new SnmpRequestPolicy(Duration.ofSeconds(5), 1, true); }
}
