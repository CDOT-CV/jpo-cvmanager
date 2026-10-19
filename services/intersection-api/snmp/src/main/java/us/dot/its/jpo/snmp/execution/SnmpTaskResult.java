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
        if (status == Status.RUNNING) return runningMessage();
        return switch (writeState) {
            case UNKNOWN -> "Write not confirmed; check device before retrying.";
            case VERIFICATION_FAILED -> "Readback did not match the requested change.";
            case VERIFIED -> verifiedMessage();
            case ACKNOWLEDGED_UNVERIFIED -> acknowledgedMessage(relevant);
            default -> terminalMessage();
        };
    }

    private String runningMessage() {
        if (deadlineExceeded) return "Deadline reached; waiting for running work to stop.";
        if (cancellationRequested) return "Cancellation requested; waiting for running work to stop.";
        return "Running.";
    }

    private String verifiedMessage() {
        return switch (status) {
            case SUCCEEDED -> "Change verified.";
            case CANCELLED -> "Change verified; task cancelled.";
            case TIMED_OUT -> "Change verified; a later step timed out.";
            default -> "Change verified; a later step failed.";
        };
    }

    private String acknowledgedMessage(SnmpStepResult<?> relevant) {
        if (status == Status.SUCCEEDED) return "Change acknowledged; verification not confirmed.";
        if (relevant != null && relevant.kind() == SnmpStep.Kind.VERIFY) {
            if (failure != null && failure.kind() == SnmpException.Kind.TIMEOUT)
                return "Change acknowledged; verification timed out.";
            return "Change acknowledged; verification incomplete.";
        }
        if (relevant != null && relevant.commands().stream().anyMatch(command -> command.status() == SnmpCommandResult.Status.READ_TIMED_OUT))
            return "Change acknowledged; a later read timed out.";
        return status == Status.CANCELLED ? "Change acknowledged; task cancelled." : "Change acknowledged; task did not finish.";
    }

    private String terminalMessage() {
        return switch (status) {
            case SUCCEEDED -> "Completed.";
            case TIMED_OUT -> timeoutMessage();
            case CANCELLED -> "Cancelled.";
            case QUEUE_EXPIRED -> "Expired while waiting; device was not contacted.";
            case REJECTED -> "Not accepted; executor unavailable.";
            default -> failure == null ? "Task failed." : SnmpMessages.failure(failure.kind());
        };
    }

    private String timeoutMessage() {
        if (deadlineExceeded) return "Task deadline reached.";
        return submissions == 0 ? "Task timed out." : "Device did not respond.";
    }

    @Override public String toString() { return summary().toString(); }
}
