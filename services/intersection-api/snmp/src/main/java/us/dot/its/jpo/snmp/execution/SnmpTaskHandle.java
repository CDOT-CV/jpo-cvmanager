package us.dot.its.jpo.snmp.execution;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Cancellation preserves results; await returns the terminal CANCELLED result, rather than discarding it. */
public final class SnmpTaskHandle {
    final SnmpTaskExecutor.Control control;
    private final SnmpTaskExecutor owner;
    SnmpTaskHandle(SnmpTaskExecutor owner, SnmpTaskExecutor.Control control) { this.owner = owner; this.control = control; }
    public String taskId() { return control.id; }
    public SnmpTaskResult snapshot() { return control.snapshot(); }
    public boolean cancel() { return owner.cancel(control); }
    public SnmpTaskResult await() throws InterruptedException { control.done.await(); return snapshot(); }
    /** A caller wait timeout does not cancel execution. */
    public SnmpTaskResult await(Duration timeout) throws InterruptedException, TimeoutException {
        if (timeout == null || timeout.isNegative()) throw new IllegalArgumentException("Invalid wait timeout");
        if (!control.done.await(timeout.toNanos(), TimeUnit.NANOSECONDS)) throw new TimeoutException("Task is not complete");
        return snapshot();
    }
}
