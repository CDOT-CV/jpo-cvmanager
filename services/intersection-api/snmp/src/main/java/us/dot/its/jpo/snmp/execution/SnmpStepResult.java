package us.dot.its.jpo.snmp.execution;

import java.time.Instant;
import java.util.List;

/**
 * Progress and call evidence for one typed step. Lists are copied; action values must be immutable.
 * A successful earlier step remains readable even if a later step fails.
 *
 * @param <T> action result type
 * @param definition original step reference used for typed lookups
 * @param value action result; use task.value(step) to require successful completion
 * @param commands recorded logical calls, not individual walk pages or protocol retries
 * @param failure safe failure evidence, or null when none was recorded
 */
public record SnmpStepResult<T>(SnmpStep<T> definition, Status status, Instant startedAt, Instant finishedAt,
        T value, List<SnmpCommandResult> commands, SnmpFailure failure) {
    /** Step outcomes distinguish unsent work, read timeouts and uncertain writes. */
    public enum Status { NOT_STARTED, RUNNING, SUCCEEDED, REJECTED, TIMED_OUT, READ_TIMED_OUT, WRITE_OUTCOME_UNKNOWN, VERIFICATION_FAILED, CANCELLED, FAILED }
    public SnmpStepResult { commands = List.copyOf(commands); }
    public String name() { return definition.name(); }
    public SnmpStep.Kind kind() { return definition.kind(); }
    @Override public String toString() { return "SnmpStepResult[name=" + name() + ", kind=" + kind() + ", status=" + status + "]"; }
}
