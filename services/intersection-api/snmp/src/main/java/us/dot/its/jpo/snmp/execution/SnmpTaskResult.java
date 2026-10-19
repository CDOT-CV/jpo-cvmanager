package us.dot.its.jpo.snmp.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import us.dot.its.jpo.snmp.client.SnmpException;
import us.dot.its.jpo.snmp.internal.SnmpMessages;

/** Progress snapshot; action values must be immutable. The handle retains results, not a global cache. */
public record SnmpTaskResult(String taskId, String name, String endpoint, Status status, WriteState writeState,
        Instant submittedAt, Instant startedAt, Instant finishedAt, Duration queueWait, Duration executionTime,
        boolean cancellationRequested, boolean deadlineExceeded, int submissions, List<SnmpStepResult<?>> steps, SnmpFailure failure) {
    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED, TIMED_OUT, CANCELLED, QUEUE_EXPIRED, REJECTED }
    public enum WriteState { NOT_SUBMITTED, REJECTED, ACKNOWLEDGED_UNVERIFIED, VERIFIED, VERIFICATION_FAILED, UNKNOWN }
    public SnmpTaskResult { steps = List.copyOf(steps); }
    public boolean terminal() { return status != Status.QUEUED && status != Status.RUNNING; }

    public <T> T value(SnmpStep<T> step) {
        var result = step.result(this);
        if (result.status() != SnmpStepResult.Status.SUCCEEDED) throw new IllegalStateException("Step has not succeeded");
        return result.value();
    }

    /** Human-readable overview without payloads, credentials or duplicate confirmation flags. */
    public SnmpTaskSummary summary() {
        var relevant = steps.stream().filter(step -> step.status() != SnmpStepResult.Status.SUCCEEDED
                && step.status() != SnmpStepResult.Status.NOT_STARTED).findFirst().orElse(null);
        return new SnmpTaskSummary(taskId, endpoint, status, message(relevant), relevant == null ? null : relevant.name(),
                queueWait.plus(executionTime).toMillis(),
                steps.stream().map(step -> new SnmpTaskSummary.Step(step.name(), step.status())).toList());
    }

    private String message(SnmpStepResult<?> relevant) {
        if (status == Status.QUEUED) return "Waiting to run.";
        if (status == Status.RUNNING) {
            if (deadlineExceeded) return "Deadline reached; waiting for running work to stop.";
            if (cancellationRequested) return "Cancellation requested; waiting for running work to stop.";
            return "Running.";
        }
        if (writeState == WriteState.UNKNOWN) return "Write not confirmed; check device before retrying.";
        if (writeState == WriteState.VERIFICATION_FAILED) return "Readback did not match the requested change.";
        if (writeState == WriteState.VERIFIED && status != Status.SUCCEEDED) {
            return status == Status.CANCELLED ? "Change verified; task cancelled."
                    : status == Status.TIMED_OUT ? "Change verified; a later step timed out." : "Change verified; a later step failed.";
        }
        if (writeState == WriteState.ACKNOWLEDGED_UNVERIFIED) {
            if (status == Status.SUCCEEDED) return "Change acknowledged; verification not confirmed.";
            boolean verifying = relevant != null && relevant.kind() == SnmpStep.Kind.VERIFY;
            if (verifying && failure != null && failure.kind() == SnmpException.Kind.TIMEOUT) return "Change acknowledged; verification timed out.";
            if (verifying) return "Change acknowledged; verification incomplete.";
            if (relevant != null && relevant.commands().stream().anyMatch(command -> command.status() == SnmpCommandResult.Status.READ_TIMED_OUT))
                return "Change acknowledged; a later read timed out.";
            return status == Status.CANCELLED ? "Change acknowledged; task cancelled." : "Change acknowledged; task did not finish.";
        }
        return switch (status) {
            case SUCCEEDED -> writeState == WriteState.VERIFIED ? "Change verified." : "Completed.";
            case TIMED_OUT -> deadlineExceeded ? "Task deadline reached." : submissions == 0 ? "Task timed out." : "Device did not respond.";
            case CANCELLED -> "Cancelled.";
            case QUEUE_EXPIRED -> "Expired while waiting; device was not contacted.";
            case REJECTED -> "Not accepted; executor unavailable.";
            default -> failure == null ? "Task failed." : SnmpMessages.failure(failure.kind());
        };
    }

    @Override public String toString() { return summary().toString(); }
}
