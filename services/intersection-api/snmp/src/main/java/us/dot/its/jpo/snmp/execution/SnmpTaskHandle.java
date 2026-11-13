package us.dot.its.jpo.snmp.execution;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Access to one submitted execution. The handle retains progress and failure evidence.
 * Cancellation is cooperative: unresponsive callback code may keep running until it returns.
 */
public final class SnmpTaskHandle {
    final SnmpTaskExecutor.Control control;
    private final SnmpTaskExecutor owner;
    SnmpTaskHandle(SnmpTaskExecutor owner, SnmpTaskExecutor.Control control) { this.owner = owner; this.control = control; }

    /**
     * Returns the correlation ID assigned to this execution.
     * @return execution ID
     */
    public String taskId() { return control.id; }

    /**
     * Returns current progress without waiting. Later snapshots may contain more evidence.
     * @return current immutable result snapshot
     */
    public SnmpTaskResult snapshot() { return control.snapshot(); }

    /**
     * Requests cancellation without discarding results or releasing a running endpoint early.
     * @return true if accepted; false if stopping, finished, or the final step has already completed
     */
    public boolean cancel() { return owner.cancel(control); }

    /**
     * Waits for completion and returns the terminal result, including failures or cancellation.
     * @return terminal result after executor cleanup
     * @throws InterruptedException if the waiting caller is interrupted; the task is not cancelled
     */
    public SnmpTaskResult await() throws InterruptedException { control.done.await(); return snapshot(); }

    /**
     * Waits up to the caller's timeout without changing the task's execution deadline.
     * A wait timeout or caller interruption does not cancel the task.
     * @param timeout nonnegative caller wait limit; zero performs an immediate check
     * @return terminal result after executor cleanup
     * @throws IllegalArgumentException if the timeout is null or negative
     * @throws InterruptedException if the caller is interrupted
     * @throws TimeoutException if the task has not completed within the wait limit
     */
    public SnmpTaskResult await(Duration timeout) throws InterruptedException, TimeoutException {
        if (timeout == null || timeout.isNegative()) throw new IllegalArgumentException("Invalid wait timeout");
        if (!control.done.await(timeout.toNanos(), TimeUnit.NANOSECONDS)) throw new TimeoutException("Task is not complete");
        return snapshot();
    }
}
