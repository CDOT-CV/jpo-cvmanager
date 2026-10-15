package us.dot.its.jpo.snmp.execution;

import java.util.List;

/** Compact presentation for applications. Detailed evidence stays on SnmpTaskResult. */
public record SnmpTaskSummary(String taskId, String target, SnmpTaskResult.Status status,
        String message, String step, long elapsedMs, List<Step> steps) {
    public record Step(String name, SnmpStepResult.Status status) {}
    public SnmpTaskSummary { steps = List.copyOf(steps); }
    @Override public String toString() {
        return status + ": " + message + " [task=" + taskId + " target=" + target
                + (step == null ? "" : " step=" + step) + " elapsed=" + elapsedMs + "ms]";
    }
}
