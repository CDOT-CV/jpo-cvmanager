package us.dot.its.jpo.snmp.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import us.dot.its.jpo.snmp.client.SnmpException;
import static org.junit.jupiter.api.Assertions.*;
import static us.dot.its.jpo.snmp.execution.SnmpTaskResult.Status.*;
import static us.dot.its.jpo.snmp.execution.SnmpTaskResult.WriteState.*;

class SnmpTaskSummaryTest {
    private SnmpTaskResult result(SnmpTaskResult.Status status, SnmpTaskResult.WriteState writeState,
            boolean cancelled, boolean deadline, SnmpStep.Kind kind) {
        var failure = new SnmpFailure(SnmpException.Kind.TIMEOUT, false, null, null);
        return new SnmpTaskResult("fixture-task", "configure", "udp:192.0.2.10:161", status, writeState,
                Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, Duration.ofMillis(30), Duration.ofMillis(2000), cancelled, deadline, 1,
                List.of(new SnmpStepResult<>(new SnmpStep<>("verify-row", kind, ctx -> "sensitive-output"), SnmpStepResult.Status.READ_TIMED_OUT, null, null,
                        "sensitive-output", List.of(), failure),
                        new SnmpStepResult<>(new SnmpStep<>("later", SnmpStep.Kind.LOCAL, ctx -> null), SnmpStepResult.Status.NOT_STARTED, null, null, null, List.of(), null)), failure);
    }

    @Test void separatesUnconfirmedWriteFromAcknowledgedWriteAndConfirmedMismatch() {
        assertEquals("Write not confirmed; check device before retrying.", result(TIMED_OUT, UNKNOWN, false, false, SnmpStep.Kind.WRITE).summary().message());
        var verifiedRead = result(TIMED_OUT, ACKNOWLEDGED_UNVERIFIED, false, false, SnmpStep.Kind.VERIFY).summary();
        assertEquals("Change acknowledged; verification timed out.", verifiedRead.message());
        assertEquals("verify-row", verifiedRead.step()); assertEquals(2030, verifiedRead.elapsedMs());
        assertEquals("Readback did not match the requested change.", result(FAILED, VERIFICATION_FAILED, false, false, SnmpStep.Kind.VERIFY).summary().message());
        assertFalse(verifiedRead.toString().contains("sensitive-output"));
        assertEquals("Change verified.", result(SUCCEEDED, VERIFIED, false, false, SnmpStep.Kind.VERIFY).summary().message());
        assertEquals("Change acknowledged; verification not confirmed.", result(SUCCEEDED, ACKNOWLEDGED_UNVERIFIED, false, false, SnmpStep.Kind.WRITE).summary().message());
        assertEquals("Completed.", result(SUCCEEDED, NOT_SUBMITTED, false, false, SnmpStep.Kind.READ).summary().message());
    }

    @Test void runningWriteIsNotReportedAsTerminalUncertaintyWhenStopIsPending() {
        assertEquals("Running.", result(RUNNING, UNKNOWN, false, false, SnmpStep.Kind.WRITE).summary().message());
        assertEquals("Cancellation requested; waiting for running work to stop.", result(RUNNING, UNKNOWN, true, false, SnmpStep.Kind.WRITE).summary().message());
        assertEquals("Deadline reached; waiting for running work to stop.", result(RUNNING, UNKNOWN, false, true, SnmpStep.Kind.WRITE).summary().message());
    }

    @Test void doesNotAssumeAnUnrelatedReadWasVerificationOrEraseEarlierVerification() {
        var read = result(TIMED_OUT, ACKNOWLEDGED_UNVERIFIED, false, false, SnmpStep.Kind.READ);
        assertEquals("Change acknowledged; task did not finish.", read.summary().message());
        assertEquals("Change verified; a later step timed out.", result(TIMED_OUT, VERIFIED, false, false, SnmpStep.Kind.READ).summary().message());
    }

    @Test void localQueueAndExecutionLimitsAreDistinctFromDeviceTimeout() {
        assertEquals("Expired while waiting; device was not contacted.", result(QUEUE_EXPIRED, NOT_SUBMITTED, false, false, SnmpStep.Kind.READ).summary().message());
        assertEquals("Not accepted; executor unavailable.", result(SnmpTaskResult.Status.REJECTED, NOT_SUBMITTED, false, false, SnmpStep.Kind.READ).summary().message());
        assertEquals("Task deadline reached.", result(TIMED_OUT, NOT_SUBMITTED, false, true, SnmpStep.Kind.READ).summary().message());
        assertEquals("Device did not respond.", result(TIMED_OUT, NOT_SUBMITTED, false, false, SnmpStep.Kind.READ).summary().message());
    }

    @Test void includesAllStepsInOrderWithoutValuesOrMutableHistory() {
        var failed = result(TIMED_OUT, ACKNOWLEDGED_UNVERIFIED, false, false, SnmpStep.Kind.VERIFY);
        var completed = new SnmpStepResult<>(new SnmpStep<>("create-row", SnmpStep.Kind.WRITE, ctx -> "sensitive-output"), SnmpStepResult.Status.SUCCEEDED,
                Instant.EPOCH, Instant.EPOCH, "sensitive-completed-output", List.of(), null);
        var history = new java.util.ArrayList<SnmpStepResult<?>>(List.of(completed));
        history.addAll(failed.steps());
        var task = new SnmpTaskResult(failed.taskId(), failed.name(), failed.endpoint(), failed.status(), failed.writeState(),
                failed.submittedAt(), failed.startedAt(), failed.finishedAt(), failed.queueWait(), failed.executionTime(),
                false, false, failed.submissions(), history, failed.failure());
        var summary = task.summary();
        assertEquals(List.of(
                new SnmpTaskSummary.Step("create-row", SnmpStepResult.Status.SUCCEEDED),
                new SnmpTaskSummary.Step("verify-row", SnmpStepResult.Status.READ_TIMED_OUT),
                new SnmpTaskSummary.Step("later", SnmpStepResult.Status.NOT_STARTED)), summary.steps());
        assertEquals("verify-row", summary.step());
        assertFalse(summary.steps().toString().contains("sensitive-"));
        assertFalse(summary.toString().contains("steps="));
        assertThrows(UnsupportedOperationException.class, () -> summary.steps().clear());
        history.clear();
        assertEquals(3, summary.steps().size());
    }
}
