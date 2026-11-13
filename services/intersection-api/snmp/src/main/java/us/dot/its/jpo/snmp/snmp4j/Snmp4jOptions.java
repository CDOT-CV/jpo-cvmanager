package us.dot.its.jpo.snmp.snmp4j;

import java.time.Duration;

/**
 * Resource and request limits for one reusable SNMP4J client.
 * Scoped task calls use their request policy for timeout/retries, while the operation deadline
 * and cache/output limits still apply. SET is never automatically replayed.
 * All durations must be from one millisecond to one day.
 *
 * @param requestTimeout direct-call attempt timeout
 * @param readRetries direct-call protocol retries, 0-10
 * @param operationDeadline budget shared by admission, discovery and all walk pages
 * @param admissionTimeout maximum wait for a concurrent-operation permit
 * @param maxConcurrentOperations number of active logical operations
 * @param maxSessions cached full credential profiles; only idle sessions can be evicted
 * @param maxTargetsPerSession peers cached in one credential profile; idle peers can be evicted
 * @param maxWalkBindings maximum bindings returned by a walk
 * @param maxWalkBytes OID/value accounting budget, at least 1024 bytes; not a total heap limit
 * @param maxBulkRepetitions zero for GETNEXT, or 1-64 for GETBULK page size
 */
public record Snmp4jOptions(Duration requestTimeout, int readRetries, Duration operationDeadline,
        Duration admissionTimeout, int maxConcurrentOperations, int maxSessions,
        int maxTargetsPerSession, int maxWalkBindings, int maxWalkBytes, int maxBulkRepetitions) {
    /** Creates options using GETNEXT walks; all other limits are supplied explicitly. */
    public Snmp4jOptions(Duration requestTimeout, int readRetries, Duration operationDeadline,
            Duration admissionTimeout, int maxConcurrentOperations, int maxSessions,
            int maxTargetsPerSession, int maxWalkBindings, int maxWalkBytes) {
        this(requestTimeout, readRetries, operationDeadline, admissionTimeout, maxConcurrentOperations,
                maxSessions, maxTargetsPerSession, maxWalkBindings, maxWalkBytes, 0);
    }

    /**
     * Validates positive capacities, duration bounds, retries and page size.
     * @throws IllegalArgumentException if any limit is outside its supported range
     */
    public Snmp4jOptions {
        for (Duration duration : new Duration[]{requestTimeout, operationDeadline, admissionTimeout}) {
            if (duration == null || duration.isZero() || duration.isNegative()
                    || duration.compareTo(Duration.ofDays(1)) > 0 || duration.toMillis() == 0) {
                throw new IllegalArgumentException("Timeouts must be between one millisecond and one day");
            }
        }
        if (readRetries < 0 || readRetries > 10 || maxConcurrentOperations < 1 || maxSessions < 1
                || maxTargetsPerSession < 1 || maxWalkBindings < 1 || maxWalkBytes < 1024
                || maxBulkRepetitions < 0 || maxBulkRepetitions > 64) {
            throw new IllegalArgumentException("Invalid SNMP resource limits");
        }
    }

    /**
     * Returns five-second requests with one read retry, a 60-second operation deadline,
     * 24 operations, 32 profiles, 1024 peers per profile and GETNEXT walks.
     * @return default client limits
     */
    public static Snmp4jOptions defaults() {
        return new Snmp4jOptions(Duration.ofSeconds(5), 1, Duration.ofSeconds(60),
                Duration.ofSeconds(5), 24, 32, 1024, 16384, 4 * 1024 * 1024);
    }
}
