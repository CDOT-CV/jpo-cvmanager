package us.dot.its.jpo.snmp.execution;

import java.time.Duration;

/**
 * Limits for one executor instance, not a scheduler or fleet polling configuration.
 *
 * @param maxConcurrentTasks running task limit; must also fit the supplied client
 * @param maxQueuedTasks waiting task limit; zero permits only immediately runnable work
 * @param shutdownTimeout maximum close wait, from zero to one minute; cannot force callbacks to stop
 */
public record SnmpExecutorOptions(int maxConcurrentTasks, int maxQueuedTasks, Duration shutdownTimeout) {
    /**
     * Validates executor limits.
     * @throws IllegalArgumentException if concurrency is below one or another limit is invalid
     */
    public SnmpExecutorOptions {
        if (maxConcurrentTasks < 1 || maxQueuedTasks < 0 || shutdownTimeout == null || shutdownTimeout.isNegative()
                || shutdownTimeout.compareTo(Duration.ofMinutes(1)) > 0) throw new IllegalArgumentException("Invalid executor limits");
    }

    /**
     * Returns 24 running tasks, 1024 queued tasks and a five-second shutdown wait.
     * @return default executor limits
     */
    public static SnmpExecutorOptions defaults() { return new SnmpExecutorOptions(24, 1024, Duration.ofSeconds(5)); }
}
