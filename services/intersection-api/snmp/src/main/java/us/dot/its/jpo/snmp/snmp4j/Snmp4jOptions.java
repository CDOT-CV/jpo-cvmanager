package us.dot.its.jpo.snmp.snmp4j;

import java.time.Duration;

/** Limits are per shared client. Read retries are protocol retries; SET is never automatically replayed. */
public record Snmp4jOptions(Duration requestTimeout, int readRetries, Duration operationDeadline,
        Duration admissionTimeout, int maxConcurrentOperations, int maxSessions,
        int maxTargetsPerSession, int maxWalkBindings, int maxWalkBytes) {
    public Snmp4jOptions {
        for (Duration duration : new Duration[]{requestTimeout, operationDeadline, admissionTimeout}) {
            if (duration == null || duration.isZero() || duration.isNegative()
                    || duration.compareTo(Duration.ofDays(1)) > 0 || duration.toMillis() == 0) {
                throw new IllegalArgumentException("Timeouts must be between one millisecond and one day");
            }
        }
        if (readRetries < 0 || readRetries > 10 || maxConcurrentOperations < 1 || maxSessions < 1
                || maxTargetsPerSession < 1 || maxWalkBindings < 1 || maxWalkBytes < 1024) {
            throw new IllegalArgumentException("Invalid SNMP resource limits");
        }
    }

    public static Snmp4jOptions defaults() {
        return new Snmp4jOptions(Duration.ofSeconds(5), 1, Duration.ofSeconds(60),
                Duration.ofSeconds(5), 24, 32, 1024, 16384, 4 * 1024 * 1024);
    }
}
