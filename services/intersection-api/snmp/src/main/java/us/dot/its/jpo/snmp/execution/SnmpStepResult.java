package us.dot.its.jpo.snmp.execution;

import java.time.Instant;
import java.util.List;

public record SnmpStepResult(String name, SnmpStep.Kind kind, Status status, Instant startedAt, Instant finishedAt,
        Object value, List<SnmpCommandResult> commands, SnmpFailure failure) {
    public enum Status { NOT_STARTED, RUNNING, SUCCEEDED, REJECTED, TIMED_OUT, READ_TIMED_OUT, WRITE_OUTCOME_UNKNOWN, VERIFICATION_FAILED, CANCELLED, FAILED }
    public SnmpStepResult {
        commands = List.copyOf(commands);
        if (value instanceof List<?> list) value = List.copyOf(list);
    }
    public <T> T valueAs(Class<T> type) { return type.cast(value); }
    @Override public String toString() { return "SnmpStepResult[name=" + name + ", kind=" + kind + ", status=" + status + "]"; }
}
