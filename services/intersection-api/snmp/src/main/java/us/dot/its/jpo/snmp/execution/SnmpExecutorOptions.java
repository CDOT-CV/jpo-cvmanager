package us.dot.its.jpo.snmp.execution;

import java.time.Duration;

public record SnmpExecutorOptions(int maxConcurrentTasks, int maxQueuedTasks, Duration shutdownTimeout) {
    public SnmpExecutorOptions {
        if (maxConcurrentTasks < 1 || maxQueuedTasks < 0 || shutdownTimeout == null || shutdownTimeout.isNegative()
                || shutdownTimeout.compareTo(Duration.ofMinutes(1)) > 0) throw new IllegalArgumentException("Invalid executor limits");
    }
    public static SnmpExecutorOptions defaults() { return new SnmpExecutorOptions(24, 1024, Duration.ofSeconds(5)); }
}
