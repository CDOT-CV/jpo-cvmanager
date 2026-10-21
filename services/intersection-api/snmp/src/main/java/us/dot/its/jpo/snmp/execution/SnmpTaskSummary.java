package us.dot.its.jpo.snmp.execution;

import java.util.List;

/**
 * Compact presentation without payloads; use SnmpTaskResult for application decisions.
 * Messages are human-readable and should not be parsed as a stable error protocol.
 *
 * @param step current or first failed step, or null when none applies
 * @param elapsedMs queue wait plus running time
 * @param steps declared steps in order, including those not started
 */
public record SnmpTaskSummary(String taskId, String target, SnmpTaskResult.Status status,
        String message, String step, long elapsedMs, List<Step> steps) {
    /** A step's display name and state, without its returned value. */
    public record Step(String name, SnmpStepResult.Status status) {}
    public SnmpTaskSummary { steps = List.copyOf(steps); }
    @Override public String toString() {
        return status + ": " + message + " [task=" + taskId + " target=" + target
                + (step == null ? "" : " step=" + step) + " elapsed=" + elapsedMs + "ms]";
    }
}
