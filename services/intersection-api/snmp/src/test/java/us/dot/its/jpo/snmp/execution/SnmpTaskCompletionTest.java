package us.dot.its.jpo.snmp.execution;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import us.dot.its.jpo.snmp.client.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the completion/stop boundary directly, without relying on timer scheduling. */
class SnmpTaskCompletionTest {
    private static final SnmpTarget TARGET = new SnmpTarget("192.0.2.10",
            new SnmpCredentials("fixture-user", "fixture-auth-pass", "fixture-privacy-pass"));
    private static final SnmpBinding BINDING = new SnmpBinding(new SnmpOid("1.3.6.1.2.1.1.3.0"), new SnmpValue.IntegerValue(1));

    @AfterEach void clearWorkerInterrupt() { Thread.interrupted(); }

    @ParameterizedTest
    @EnumSource(value = SnmpException.Kind.class, names = {"CANCELLED", "TIMEOUT"})
    void stopAfterFinalStepCannotOverrideAcknowledgedWrite(SnmpException.Kind reason) {
        var step = new SnmpStep<>("write", SnmpStep.Kind.WRITE, ctx -> true);
        var control = new SnmpTaskExecutor.Control(new SnmpTask("write", TARGET, List.of(step)), SnmpTaskOptions.defaults());
        control.start();
        control.workerStarted();
        var mutable = new SnmpTaskExecutor.MutableStep<>(step);
        control.steps.set(0, mutable);
        control.startStep(mutable);
        var command = control.startCommand("SET", List.of(BINDING.oid()), List.of(BINDING));
        control.beforeRequest(command, true);
        control.commandSucceeded(command, List.of(BINDING));
        control.stepSucceeded(mutable, true);

        assertFalse(control.requestStop(reason));
        assertFalse(Thread.currentThread().isInterrupted());
        // Final bookkeeping may run after the deadline; completed steps remain authoritative.
        control.deadline = System.nanoTime() - 1;
        control.finish(SnmpTaskResult.Status.SUCCEEDED, null);
        var result = control.snapshot();
        assertEquals(SnmpTaskResult.Status.SUCCEEDED, result.status());
        assertEquals(SnmpTaskResult.WriteState.ACKNOWLEDGED_UNVERIFIED, result.writeState());
        assertNull(result.failure());
        assertFalse(result.cancellationRequested());
        assertFalse(result.deadlineExceeded());
    }

    @ParameterizedTest
    @EnumSource(value = SnmpException.Kind.class, names = {"CANCELLED", "TIMEOUT"})
    void stopBeforeCallbackCompletesStillApplies(SnmpException.Kind reason) {
        var step = new SnmpStep<>("local", SnmpStep.Kind.LOCAL, ctx -> true);
        var control = new SnmpTaskExecutor.Control(new SnmpTask("local", TARGET, List.of(step)), SnmpTaskOptions.defaults());
        control.start();
        control.workerStarted();
        var mutable = new SnmpTaskExecutor.MutableStep<>(step);
        control.steps.set(0, mutable);
        control.startStep(mutable);
        try {
            assertTrue(control.requestStop(reason));
            control.stepSucceeded(mutable, true);
            control.finish(SnmpTaskResult.Status.SUCCEEDED, null);
            assertEquals(reason == SnmpException.Kind.TIMEOUT ? SnmpTaskResult.Status.TIMED_OUT : SnmpTaskResult.Status.CANCELLED,
                    control.snapshot().status());
        } finally {
            Thread.interrupted();
        }
    }
}
