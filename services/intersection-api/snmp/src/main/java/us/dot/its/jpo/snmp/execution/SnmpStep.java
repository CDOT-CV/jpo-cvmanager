package us.dot.its.jpo.snmp.execution;

import java.util.Objects;

/**
 * A typed action within a task. Keep the same step reference to retrieve its result safely.
 *
 * @param <T> immutable action result type
 * @param name unique, log-safe step label
 * @param kind controls write admission and verification behavior
 * @param action synchronous work performed on the task worker
 */
public record SnmpStep<T>(String name, Kind kind, Action<T> action) {
    /**
     * Only WRITE may send SET; VERIFY must return Boolean.TRUE. WRITE may return
     * a verified {@link us.dot.its.jpo.snmp.client.SnmpWriteResult}.
     */
    public enum Kind { READ, WRITE, VERIFY, LOCAL }

    /**
     * Application callback running within a task deadline. Use only the supplied context for SNMP.
     * Callbacks must respond to interruption and must not spawn child SNMP calls.
     * @param <T> immutable result type
     */
    @FunctionalInterface public interface Action<T> {
        // Application callbacks may use checked exceptions from storage or other device procedures.
        // The executor translates them into safe task failures; callers need not wrap each one.
        /**
         * Runs this step. Checked exceptions are translated into safe task failure evidence.
         * @param context worker-bound target, client and earlier step values
         * @return step result, which may be null
         * @throws Exception if the procedure cannot complete, including interruption
         */
        @SuppressWarnings("java:S112")
        T execute(SnmpTaskContext context) throws Exception;
    }

    /**
     * Validates the name and requires a kind and action.
     * @throws IllegalArgumentException if the name is not a valid 1-80 character label
     */
    public SnmpStep {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("Invalid step name");
        Objects.requireNonNull(kind); Objects.requireNonNull(action);
    }

    /**
     * Finds this exact step in a task snapshot, including incomplete or failed progress.
     * Matching names alone do not establish type safety.
     * @param task snapshot from an execution of the task containing this step
     * @return typed progress and evidence for this step
     * @throws IllegalArgumentException if this step definition does not belong to the task
     */
    @SuppressWarnings("unchecked")
    public SnmpStepResult<T> result(SnmpTaskResult task) {
        return (SnmpStepResult<T>) task.steps().stream().filter(step -> step.definition() == this)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Step does not belong to this task"));
    }

    @Override public String toString() { return "SnmpStep[name=" + name + ", kind=" + kind + "]"; }
}
