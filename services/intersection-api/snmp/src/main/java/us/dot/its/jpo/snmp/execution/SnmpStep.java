package us.dot.its.jpo.snmp.execution;

import java.util.Objects;

/** A typed step. Return immutable data; WRITE may return SnmpWriteResult after verifying its own procedure. */
public record SnmpStep<T>(String name, Kind kind, Action<T> action) {
    public enum Kind { READ, WRITE, VERIFY, LOCAL }
    @FunctionalInterface public interface Action<T> { T execute(SnmpTaskContext context) throws Exception; }
    public SnmpStep {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("Invalid step name");
        Objects.requireNonNull(kind); Objects.requireNonNull(action);
    }

    /** Identity, rather than a matching name, proves the stored value has this step's type. */
    @SuppressWarnings("unchecked")
    public SnmpStepResult<T> result(SnmpTaskResult task) {
        return (SnmpStepResult<T>) task.steps().stream().filter(step -> step.definition() == this)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Step does not belong to this task"));
    }

    @Override public String toString() { return "SnmpStep[name=" + name + ", kind=" + kind + "]"; }
}
