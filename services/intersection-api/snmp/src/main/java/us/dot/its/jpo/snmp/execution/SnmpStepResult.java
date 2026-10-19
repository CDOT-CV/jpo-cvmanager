package us.dot.its.jpo.snmp.execution;

import java.time.Instant;
import java.util.List;

public record SnmpStepResult<T>(SnmpStep<T> definition, Status status, Instant startedAt, Instant finishedAt,
        T value, List<SnmpCommandResult> commands, SnmpFailure failure) {
    public enum Status { NOT_STARTED, RUNNING, SUCCEEDED, REJECTED, TIMED_OUT, READ_TIMED_OUT, WRITE_OUTCOME_UNKNOWN, VERIFICATION_FAILED, CANCELLED, FAILED }
    public SnmpStepResult { commands = List.copyOf(commands); }
    public String name() { return definition.name(); }
    public SnmpStep.Kind kind() { return definition.kind(); }
    @Override public String toString() { return "SnmpStepResult[name=" + name() + ", kind=" + kind() + ", status=" + status + "]"; }
}
