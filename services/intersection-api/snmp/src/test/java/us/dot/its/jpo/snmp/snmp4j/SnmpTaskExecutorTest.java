package us.dot.its.jpo.snmp.snmp4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import us.dot.its.jpo.snmp.client.*;
import us.dot.its.jpo.snmp.execution.*;
import static org.junit.jupiter.api.Assertions.*;
import static us.dot.its.jpo.snmp.execution.SnmpStep.Kind.*;

@Timeout(30)
class SnmpTaskExecutorTest {
    private static final SnmpCredentials CREDS = new SnmpCredentials("fixture-user", "fixture-auth-pass", "fixture-privacy-pass");
    private static final SnmpOid OID = new SnmpOid("1.3.6.1.4.1.1206.4.2.18.12.1.0");
    private static Snmp4jClient client() {
        return new Snmp4jClient(new Snmp4jOptions(Duration.ofMillis(500), 1, Duration.ofSeconds(5), Duration.ofMillis(50), 8, 8, 200, 100, 4096, 16));
    }
    private static SnmpTaskOptions policy(int timeoutMs, int retries, boolean rediscovery, int budgetMs, int maxRequests) {
        return new SnmpTaskOptions(Duration.ofSeconds(5), Duration.ofMillis(budgetMs),
                new SnmpRequestPolicy(Duration.ofMillis(timeoutMs), retries, rediscovery), maxRequests);
    }
    private static SnmpTaskExecutor executor(SnmpClient client, int concurrency, int queued) {
        return new SnmpTaskExecutor(client, new SnmpExecutorOptions(concurrency, queued, Duration.ofSeconds(1)));
    }
    private static SnmpStep<List<SnmpBinding>> read(String name) { return new SnmpStep<>(name, READ, ctx -> ctx.client().get(ctx.target(), List.of(OID))); }
    private static SnmpStep<List<SnmpBinding>> write(String name, int value) {
        return new SnmpStep<>(name, WRITE, ctx -> ctx.client().set(ctx.target(), List.of(new SnmpBinding(OID, new SnmpValue.IntegerValue(value)))));
    }
    private static SnmpTask task(SnmpTarget target, SnmpStep<?>... steps) { return new SnmpTask("fixture-task", target, List.of(steps)); }
    private static SnmpTaskResult result(SnmpTaskHandle handle) throws Exception { return handle.await(Duration.ofSeconds(10)); }

    @Test void hundredPollTasksKeepHealthyReadsIndependentOfOfflineTimeouts() throws Exception {
        try (var healthy = new LoopbackAgent(CREDS); var offline = new LoopbackAgent(CREDS);
                var client = client(); var executor = executor(client, 8, 128)) {
            var second = new SnmpOid("1.3.6.1.4.1.1206.4.2.18.12.2.0");
            for (var agent : List.of(healthy, offline)) {
                agent.integer(OID.value(), 1); agent.integer(second.value(), 2);
                client.get(agent.target(), List.of(OID, second)); // Discover before measuring offline polls.
            }
            offline.dropReads = true;
            var handles = new java.util.ArrayList<SnmpTaskHandle>();
            for (int i = 0; i < 100; i++) {
                var target = i % 5 == 0 ? offline.target() : healthy.target();
                handles.add(executor.submit(task(target, new SnmpStep<>("status", READ,
                        ctx -> ctx.client().get(ctx.target(), List.of(OID, second)))), policy(80, 0, false, 1000, 1)));
            }
            for (int i = 0; i < handles.size(); i++) {
                var outcome = result(handles.get(i));
                assertEquals(i % 5 == 0 ? SnmpTaskResult.Status.TIMED_OUT : SnmpTaskResult.Status.SUCCEEDED, outcome.status());
                assertEquals(1, outcome.submissions());
                assertEquals(SnmpTaskResult.WriteState.NOT_SUBMITTED, outcome.writeState());
                if (i % 5 != 0) assertEquals(2, outcome.steps().getFirst().commands().getFirst().response().size());
            }
            assertEquals(0, client.diagnostics().pendingRequests());
            assertEquals(0, healthy.writes.get() + offline.writes.get());
        }
    }

    @Test void preservesValuesAcknowledgementVerificationAndImmutableSnapshots() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client, 2, 10)) {
            agent.integer(OID.value(), 1);
            var before = read("before");
            var outcome = result(executor.submit(task(agent.target(), before, write("change", 2),
                    new SnmpStep<>("verify", VERIFY, ctx -> ctx.client().get(ctx.target(), List.of(OID)).getFirst().value().equals(new SnmpValue.IntegerValue(2))),
                    new SnmpStep<>("local", LOCAL, ctx -> {
                        assertThrows(UnsupportedOperationException.class, () -> ctx.value(before).clear());
                        return ctx.value(before).size();
                    })), policy(300, 0, false, 3000, 10)));
            assertEquals(SnmpTaskResult.Status.SUCCEEDED, outcome.status());
            assertEquals(SnmpTaskResult.WriteState.VERIFIED, outcome.writeState());
            assertEquals(3, outcome.submissions()); assertEquals(1, outcome.steps().get(3).value());
            assertEquals(new SnmpValue.IntegerValue(1), outcome.value(before).getFirst().value());
            assertTrue(outcome.steps().stream().allMatch(step -> step.status() == SnmpStepResult.Status.SUCCEEDED));
            assertThrows(UnsupportedOperationException.class, () -> outcome.steps().clear());
            assertThrows(UnsupportedOperationException.class, () -> outcome.steps().getFirst().commands().getFirst().response().clear());
        }
    }

    @Test void lostWriteRetainsIntentStopsLaterStepsAndNeverReplaysEvenIfActionCatchesFailure() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client, 2, 10)) {
            agent.integer(OID.value(), 1); agent.dropWrites = true;
            var outcome = result(executor.submit(task(agent.target(), read("before"), new SnmpStep<>("change", WRITE, ctx -> {
                try { ctx.client().set(ctx.target(), List.of(new SnmpBinding(OID, new SnmpValue.IntegerValue(9)))); }
                catch (SnmpException ignored) { /* A caller must not be able to hide or blindly replay this failure. */ }
                return ctx.client().set(ctx.target(), List.of(new SnmpBinding(OID, new SnmpValue.IntegerValue(9))));
            }), read("after")), policy(100, 5, false, 3000, 0)));
            assertEquals(SnmpTaskResult.WriteState.UNKNOWN, outcome.writeState());
            assertEquals(SnmpStepResult.Status.SUCCEEDED, outcome.steps().getFirst().status());
            assertEquals(SnmpStepResult.Status.WRITE_OUTCOME_UNKNOWN, outcome.steps().get(1).status());
            assertEquals(SnmpStepResult.Status.NOT_STARTED, outcome.steps().get(2).status());
            var command = outcome.steps().get(1).commands().getFirst();
            assertEquals(SnmpCommandResult.Status.WRITE_OUTCOME_UNKNOWN, command.status());
            assertEquals(new SnmpValue.IntegerValue(9), command.requestedBindings().getFirst().value());
            assertEquals(1, command.submissions()); assertEquals(1, agent.writes.get());
        }
    }

    @Test void acknowledgedWriteAndReadbackTimeoutRemainDistinct() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client, 2, 10)) {
            var outcome = result(executor.submit(task(agent.target(), write("change", 4), new SnmpStep<>("verify", VERIFY, ctx -> {
                agent.dropReads = true;
                ctx.client().get(ctx.target(), List.of(OID)); return true;
            }), read("later")), policy(100, 0, false, 2000, 0)));
            assertEquals(SnmpTaskResult.Status.TIMED_OUT, outcome.status());
            assertEquals(SnmpTaskResult.WriteState.ACKNOWLEDGED_UNVERIFIED, outcome.writeState());
            assertEquals(SnmpCommandResult.Status.SUCCEEDED, outcome.steps().getFirst().commands().getFirst().status());
            assertFalse(outcome.steps().getFirst().commands().getFirst().response().isEmpty());
            assertEquals(SnmpStepResult.Status.READ_TIMED_OUT, outcome.steps().get(1).status());
            assertEquals(SnmpStepResult.Status.NOT_STARTED, outcome.steps().get(2).status());
        }
    }

    @Test void verifiedMismatchAndAgentRejectionAreNotLostWriteReplies() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client, 2, 10)) {
            var mismatch = result(executor.submit(task(agent.target(), write("change", 4), new SnmpStep<>("verify", VERIFY, ctx -> false))));
            assertEquals(SnmpTaskResult.WriteState.VERIFICATION_FAILED, mismatch.writeState());
            assertEquals(SnmpStepResult.Status.VERIFICATION_FAILED, mismatch.steps().get(1).status());
            agent.rejectWrites = true;
            var rejection = result(executor.submit(task(agent.target(), write("change", 5))));
            assertEquals(SnmpTaskResult.WriteState.REJECTED, rejection.writeState());
            assertEquals(17, rejection.failure().agentErrorStatus());
            assertFalse(rejection.failure().writeOutcomeUnknown());
        }
    }

    @Test void allCallsShareOneDeadlineAndLaterWriteIsNeverStarted() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client, 2, 10)) {
            agent.integer(OID.value(), 1); client.get(agent.target(), List.of(OID)); agent.delayMillis = 100;
            var outcome = result(executor.submit(task(agent.target(), read("first"), read("second"), write("never", 5)), policy(500, 0, false, 175, 0)));
            assertEquals(SnmpTaskResult.Status.TIMED_OUT, outcome.status()); assertTrue(outcome.deadlineExceeded());
            assertEquals(SnmpStepResult.Status.SUCCEEDED, outcome.steps().getFirst().status());
            assertEquals(SnmpStepResult.Status.NOT_STARTED, outcome.steps().get(2).status());
            assertEquals(0, agent.writes.get());
            assertTrue(outcome.executionTime().compareTo(Duration.ofSeconds(1)) < 0);
            assertEquals(0, client.diagnostics().pendingRequests());
        }
    }

    @Test void queueExpiryCapacityAndQueuedCancellationNeverBecomeDeviceTimeouts() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client, 1, 1)) {
            agent.integer(OID.value(), 1); client.get(agent.target(), List.of(OID)); agent.hold = true;
            var active = executor.submit(task(agent.target(), read("held")), policy(2000, 0, false, 4000, 0));
            Snmp4jClientTest.await(() -> agent.active.get() == 1);
            var waiting = executor.submit(task(agent.target(), read("queued")), new SnmpTaskOptions(Duration.ofMillis(80), Duration.ofSeconds(1), SnmpRequestPolicy.defaults(), 0));
            var rejected = result(executor.submit(task(agent.target(), read("rejected"))));
            assertEquals(SnmpTaskResult.Status.REJECTED, rejected.status());
            assertEquals(SnmpTaskResult.Status.QUEUE_EXPIRED, result(waiting).status());
            assertEquals(0, waiting.snapshot().submissions()); assertNull(waiting.snapshot().startedAt());
            var cancelled = executor.submit(task(agent.target(), read("cancelled")));
            assertTrue(cancelled.cancel()); assertEquals(SnmpTaskResult.Status.CANCELLED, result(cancelled).status());
            assertEquals(SnmpTaskResult.Status.RUNNING, active.snapshot().status());
            assertTrue(active.cancel()); assertEquals(SnmpTaskResult.Status.CANCELLED, result(active).status());
            assertFalse(active.cancel());
        }
    }

    @Test void queueWaitingDoesNotConsumeExecutionBudgetAndCallerWaitTimeoutDoesNotCancel() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client, 1, 10)) {
            agent.integer(OID.value(), 1); client.get(agent.target(), List.of(OID));
            var active = executor.submit(task(agent.target(), new SnmpStep<>("hold", LOCAL, ctx -> {
                entered.countDown(); release.await(); return null;
            })));
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS));
                var waiting = executor.submit(task(agent.target(), read("read")), policy(100, 0, false, 100, 0));
                assertThrows(java.util.concurrent.TimeoutException.class, () -> waiting.await(Duration.ofMillis(150)));
                assertEquals(SnmpTaskResult.Status.QUEUED, waiting.snapshot().status());
                assertFalse(waiting.snapshot().cancellationRequested());
                release.countDown(); result(active);
                var outcome = result(waiting);
                assertEquals(SnmpTaskResult.Status.SUCCEEDED, outcome.status());
                assertTrue(outcome.queueWait().compareTo(Duration.ofMillis(100)) > 0);
            } finally { release.countDown(); }
        } finally { release.countDown(); }
    }

    @Test void busyEndpointDoesNotBlockOtherDevicesAndSameEndpointNeverOverlaps() throws Exception {
        try (var first = new LoopbackAgent(CREDS); var second = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client, 2, 10)) {
            first.integer(OID.value(), 1); second.integer(OID.value(), 2);
            client.get(first.target(), List.of(OID)); first.hold = true;
            var active = executor.submit(task(first.target(), read("held")), policy(2000, 0, false, 4000, 0));
            Snmp4jClientTest.await(() -> first.active.get() == 1);
            var same = executor.submit(task(first.target(), read("same")));
            var other = executor.submit(task(second.target(), read("other")));
            assertEquals(SnmpTaskResult.Status.SUCCEEDED, result(other).status());
            assertEquals(SnmpTaskResult.Status.QUEUED, same.snapshot().status());
            first.release(); result(active); assertEquals(SnmpTaskResult.Status.SUCCEEDED, result(same).status());
            assertEquals(1, first.maximum.get());
        }
    }

    @Test void retryRediscoveryAndWalkSubmissionBudgetsAreConfigurable() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client, 2, 10)) {
            agent.integer(OID.value(), 1); client.get(agent.target(), List.of(OID)); agent.dropReads = true;
            int before = agent.reads.get();
            var noRecovery = result(executor.submit(task(agent.target(), read("read")), policy(80, 1, false, 2000, 0)));
            assertEquals(2, agent.reads.get() - before); assertEquals(1, noRecovery.submissions());
            agent.dropReads = false; client.get(agent.target(), List.of(OID)); agent.dropReads = true;
            var recovery = result(executor.submit(task(agent.target(), read("read")), policy(80, 0, true, 2000, 0)));
            assertEquals(2, recovery.submissions());
            agent.dropReads = false;
            // Require more than two bulk pages so the third submission hits the task budget.
            for (int i = 1; i < 40; i++) agent.integer("1.3.6.1.4.1.1206.4.2.18.12.2." + i, i);
            var limited = result(executor.submit(task(agent.target(), new SnmpStep<>("walk", READ, ctx -> ctx.client().walk(ctx.target(), new SnmpOid("1.3.6.1.4.1.1206.4.2.18.12"))), write("never", 5)), policy(300, 0, false, 2000, 2)));
            assertEquals(SnmpException.Kind.REQUEST_LIMIT, limited.failure().kind()); assertEquals(2, limited.submissions());
            assertTrue(limited.steps().getFirst().commands().getFirst().response().isEmpty());
            assertEquals(0, agent.writes.get());
        }
    }

    @Test void disablingImmediateRediscoveryStillAllowsNextPollToRecoverAnEngineReplacement() throws Exception {
        try (var client = client(); var executor = executor(client, 2, 10)) {
            int port;
            try (var original = new LoopbackAgent(CREDS)) {
                port = original.port(); original.integer(OID.value(), 1); client.get(original.target(), List.of(OID));
            }
            try (var replacement = new LoopbackAgent(CREDS, port, 1, "800000000506070809")) {
                replacement.integer(OID.value(), 2);
                var options = policy(100, 0, false, 2000, 0);
                var initial = result(executor.submit(task(replacement.target(), read("measure")), options));
                assertEquals(SnmpTaskResult.Status.TIMED_OUT, initial.status()); assertEquals(1, initial.submissions());
                var next = result(executor.submit(task(replacement.target(), read("measure")), options));
                assertEquals(SnmpTaskResult.Status.SUCCEEDED, next.status()); assertEquals(1, next.submissions());
            }
        }
    }

    @Test void commandHistoryHasAnIndependentBoundEvenWithoutASubmissionCap() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client, 2, 10)) {
            agent.integer(OID.value(), 1);
            var options = new SnmpTaskOptions(Duration.ofSeconds(2), Duration.ofSeconds(2), SnmpRequestPolicy.defaults(), 0, 2);
            var outcome = result(executor.submit(task(agent.target(), new SnmpStep<>("loop", READ, ctx -> {
                ctx.client().get(ctx.target(), List.of(OID)); ctx.client().get(ctx.target(), List.of(OID));
                try { ctx.client().get(ctx.target(), List.of(OID)); } catch (SnmpException ignored) { /* Verify that the executor retains a caught command-history failure. */ }
                return "must-not-be-success";
            })), options));
            assertEquals(SnmpTaskResult.Status.FAILED, outcome.status());
            assertEquals(SnmpException.Kind.OUTPUT_LIMIT, outcome.failure().kind());
            assertEquals(2, outcome.steps().getFirst().commands().size()); assertEquals(2, agent.reads.get());
        }
    }

    @Test void cancellationAndShutdownPreserveUncertainWriteEvidence() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client, 1, 10)) {
            agent.integer(OID.value(), 1); client.get(agent.target(), List.of(OID)); agent.hold = true;
            var active = executor.submit(task(agent.target(), write("change", 8)), policy(2000, 0, false, 4000, 0));
            Snmp4jClientTest.await(() -> agent.writes.get() == 1);
            var queued = executor.submit(task(agent.target(), read("queued")));
            executor.close();
            assertEquals(SnmpTaskResult.Status.CANCELLED, result(active).status());
            assertEquals(SnmpTaskResult.WriteState.UNKNOWN, active.snapshot().writeState());
            assertEquals(SnmpTaskResult.Status.CANCELLED, result(queued).status()); assertEquals(0, queued.snapshot().submissions());
            assertEquals(SnmpTaskResult.Status.REJECTED, result(executor.submit(task(agent.target(), read("closed")))).status());
            assertEquals(0, client.diagnostics().pendingRequests());
        }
    }

    @Test void taskContextRejectsHiddenWritesOtherTargetsAndCrossThreadAccess() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var other = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client, 2, 10)) {
            var hidden = result(executor.submit(task(agent.target(), new SnmpStep<>("hidden", READ, ctx -> ctx.client().set(ctx.target(), List.of(new SnmpBinding(OID, new SnmpValue.IntegerValue(1))))))));
            assertEquals(SnmpTaskResult.Status.FAILED, hidden.status()); assertEquals(0, agent.writes.get());
            var wrongTarget = result(executor.submit(task(agent.target(), new SnmpStep<>("wrong", READ, ctx -> ctx.client().get(other.target(), List.of(OID))))));
            assertEquals(0, wrongTarget.submissions()); assertEquals(SnmpTaskResult.Status.FAILED, wrongTarget.status());
            var leaked = new AtomicReference<SnmpTaskContext>();
            result(executor.submit(task(agent.target(), new SnmpStep<>("capture", LOCAL, ctx -> { leaked.set(ctx); return null; }))));
            assertThrows(IllegalStateException.class, () -> leaked.get().client());
            assertThrows(IllegalArgumentException.class, () -> executor(client, 9, 10));
        }
    }

    @Test void uncooperativeCodeIsNotReportedCompleteOrAllowedToOverlapAfterDeadline() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var agent = new LoopbackAgent(CREDS); var client = client();
                var executor = new SnmpTaskExecutor(client, new SnmpExecutorOptions(1, 10, Duration.ofMillis(20)))) {
            var active = executor.submit(task(agent.target(), new SnmpStep<>("local", LOCAL, ctx -> {
                entered.countDown();
                while (release.getCount() != 0) { try { release.await(); } catch (InterruptedException ignored) { /* Simulate a callback that ignores cancellation until explicitly released. */ } }
                return "completed-local-work";
            })), policy(100, 0, false, 80, 0));
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS));
                Snmp4jClientTest.await(() -> active.snapshot().deadlineExceeded());
                assertEquals(SnmpTaskResult.Status.RUNNING, active.snapshot().status());
                executor.close();
                assertEquals(SnmpTaskResult.Status.RUNNING, active.snapshot().status());
                assertEquals(1, executor.diagnostics().runningTasks());
            } finally { release.countDown(); }
            assertEquals(SnmpTaskResult.Status.TIMED_OUT, result(active).status());
            assertEquals("completed-local-work", active.snapshot().steps().getFirst().value());
        } finally { release.countDown(); }
    }

    @Test void checkedCallbackFailuresAreReportedWithoutExposingTheirMessages() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client, 1, 10)) {
            var failed = result(executor.submit(task(agent.target(), new SnmpStep<>("local", LOCAL, ctx -> {
                throw new java.io.IOException("sensitive-application-details");
            }), read("never"))));
            assertEquals(SnmpTaskResult.Status.FAILED, failed.status());
            assertEquals(SnmpException.Kind.EXECUTION, failed.failure().kind());
            assertEquals(SnmpStepResult.Status.FAILED, failed.steps().getFirst().status());
            assertEquals(SnmpStepResult.Status.NOT_STARTED, failed.steps().get(1).status());
            assertFalse(failed.toString().contains("sensitive-application-details"));
            var interrupted = result(executor.submit(task(agent.target(), new SnmpStep<>("interrupt", LOCAL, ctx -> {
                throw new InterruptedException("sensitive-application-details");
            }))));
            assertEquals(SnmpTaskResult.Status.CANCELLED, interrupted.status());
            assertEquals(SnmpException.Kind.CANCELLED, interrupted.failure().kind());
            assertEquals(SnmpStepResult.Status.CANCELLED, interrupted.steps().getFirst().status());
            assertEquals(0, agent.reads.get());
        }
    }

    @Test void errorsPropagateButReleaseTheEndpointAndPreserveUncertainWrites() throws Exception {
        var error = new LinkageError("fixture-error");
        var escaped = new AtomicReference<Throwable>();
        var transport = new SnmpClient() {
            private SnmpCallContext scope;
            @Override public SnmpClient scoped(SnmpCallContext context) { scope = context; return this; }
            @Override public List<SnmpBinding> get(SnmpTarget target, List<SnmpOid> oids) { throw new UnsupportedOperationException(); }
            @Override public List<SnmpBinding> getNext(SnmpTarget target, List<SnmpOid> oids) { throw new UnsupportedOperationException(); }
            @Override public List<SnmpBinding> walk(SnmpTarget target, SnmpOid root) { throw new UnsupportedOperationException(); }
            @Override public List<SnmpBinding> set(SnmpTarget target, List<SnmpBinding> bindings) {
                scope.beforeRequest(true);
                throw error;
            }
        };
        var target = new SnmpTarget("192.0.2.10", CREDS);
        try (var executor = executor(transport, 1, 10)) {
            var failure = result(executor.submit(task(target, new SnmpStep<>("write", WRITE, ctx -> {
                Thread.currentThread().setUncaughtExceptionHandler((thread, thrown) -> escaped.set(thrown));
                return ctx.client().set(ctx.target(), List.of(new SnmpBinding(OID, new SnmpValue.IntegerValue(1))));
            }), read("never"))));
            Snmp4jClientTest.await(() -> escaped.get() != null);
            assertSame(error, escaped.get());
            assertEquals(SnmpTaskResult.Status.FAILED, failure.status());
            assertEquals(SnmpTaskResult.WriteState.UNKNOWN, failure.writeState());
            assertEquals(SnmpStepResult.Status.WRITE_OUTCOME_UNKNOWN, failure.steps().getFirst().status());
            assertEquals(SnmpCommandResult.Status.WRITE_OUTCOME_UNKNOWN, failure.steps().getFirst().commands().getFirst().status());
            assertEquals(SnmpStepResult.Status.NOT_STARTED, failure.steps().get(1).status());
            assertEquals(1, failure.submissions());
            var next = result(executor.submit(task(target, new SnmpStep<>("next", LOCAL, ctx -> true))));
            assertEquals(SnmpTaskResult.Status.SUCCEEDED, next.status());
            assertEquals(0, executor.diagnostics().activeTargets());
        }
    }

    @Test void typedStepsRejectForeignReferencesAndUnavailableResults() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client, 2, 10)) {
            var first = new SnmpStep<>("first", LOCAL, ctx -> 42);
            var later = new SnmpStep<>("later", LOCAL, ctx -> "unused");
            var list = new SnmpStep<>("list", LOCAL, ctx -> List.of(1, 2));
            var second = new SnmpStep<>("second", LOCAL, ctx -> {
                assertThrows(IllegalStateException.class, () -> ctx.value(later));
                assertThrows(UnsupportedOperationException.class, () -> ctx.value(list).clear());
                return "value=" + ctx.value(first);
            });
            var outcome = result(executor.submit(task(agent.target(), first, list, second, later)));
            assertEquals(42, outcome.value(first));
            assertThrows(UnsupportedOperationException.class, () -> outcome.value(list).clear());
            assertEquals("value=42", outcome.value(second));
            var foreign = new SnmpStep<>("first", LOCAL, ctx -> "different type");
            assertThrows(IllegalArgumentException.class, () -> outcome.value(foreign));
            assertEquals(0, agent.reads.get() + agent.writes.get());
        }
    }

    @Test void verifiedProcedureDoesNotVerifyAnUnrelatedOrLaterWrite() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client, 2, 10)) {
            var noOp = new SnmpStep<>("no-op", WRITE, ctx -> new SnmpWriteResult<Void>(false, null));
            var unverified = result(executor.submit(task(agent.target(), write("first", 1), noOp)));
            assertEquals(SnmpTaskResult.WriteState.ACKNOWLEDGED_UNVERIFIED, unverified.writeState());
            var verified = new SnmpStep<>("verified", WRITE, ctx -> {
                ctx.client().set(ctx.target(), List.of(new SnmpBinding(OID, new SnmpValue.IntegerValue(2))));
                var value = ctx.client().get(ctx.target(), List.of(OID)).getFirst().value();
                assertEquals(new SnmpValue.IntegerValue(2), value);
                return new SnmpWriteResult<>(true, value);
            });
            assertEquals(SnmpTaskResult.WriteState.VERIFIED,
                    result(executor.submit(task(agent.target(), verified))).writeState());
            var later = result(executor.submit(task(agent.target(), verified, write("later", 3))));
            assertEquals(SnmpTaskResult.WriteState.ACKNOWLEDGED_UNVERIFIED, later.writeState());
            assertEquals(SnmpTaskResult.Status.SUCCEEDED, later.status());
        }
    }

    @Test void repeatedFleetBatchesBoundWorkIsolateTimeoutsAndRecoverReturningDevices() throws Exception {
        var agents = new ArrayList<LoopbackAgent>();
        var endpoints = new HashSet<String>();
        try (var client = client(); var executor = executor(client, 8, 1024)) {
            for (int i = 0; i < 100; i++) {
                var credentials = new SnmpCredentials("operator-" + i % 4, "fixture-auth-" + i % 8, "fixture-privacy-" + i % 8);
                var agent = new LoopbackAgent(credentials, 0, 1, String.format("8000000001%08x", i));
                agent.integer(OID.value(), i); agents.add(agent);
                assertTrue(endpoints.add(agent.target().agent()), "Test agents share endpoint " + agent.target().agent());
                // This test measures repeated fleet polls, not simultaneous cold engine/key setup.
                // Verify each device/profile before taking selected peers offline. Discovery and
                // credential isolation also have dedicated tests with their own request budgets.
                assertEquals(new SnmpValue.IntegerValue(i), client.get(agent.target(), List.of(OID)).getFirst().value());
                agent.dropReads = i % 5 == 0;
            }
            for (int cycle = 0; cycle < 3; cycle++) {
                var handles = new ArrayList<SnmpTaskHandle>();
                // Leave room for CI scheduling and rediscovery of returning peers. Tight deadline
                // behavior is tested separately using delayed/held responses, not a fleet benchmark.
                for (var agent : agents) handles.add(executor.submit(task(agent.target(), read("measure")), policy(500, 0, false, 2000, 0)));
                int success = 0;
                int timeouts = 0;
                for (int i = 0; i < handles.size(); i++) {
                    var outcome = result(handles.get(i));
                    boolean offline = cycle == 0 && i % 5 == 0;
                    String details = "cycle=" + cycle + " device=" + i + " " + outcome.summary()
                            + " queue=" + outcome.queueWait() + " run=" + outcome.executionTime();
                    assertEquals(offline ? SnmpTaskResult.Status.TIMED_OUT : SnmpTaskResult.Status.SUCCEEDED,
                            outcome.status(), details);
                    assertEquals(1, outcome.submissions(), details);
                    if (offline) {
                        timeouts++;
                        assertEquals(SnmpStepResult.Status.READ_TIMED_OUT, outcome.steps().getFirst().status(), details);
                    } else {
                        success++;
                        assertEquals(new SnmpValue.IntegerValue(i),
                                outcome.steps().getFirst().commands().getFirst().response().getFirst().value(), details);
                    }
                }
                assertEquals(cycle == 0 ? 80 : 100, success); assertEquals(cycle == 0 ? 20 : 0, timeouts);
                agents.forEach(agent -> agent.dropReads = false);
                Snmp4jClientTest.await(() -> executor.diagnostics().runningTasks() == 0);
                assertEquals(0, executor.diagnostics().queuedTasks()); assertEquals(0, client.diagnostics().pendingRequests());
                assertTrue(client.diagnostics().cachedSessions() <= 8);
                assertEquals(0, executor.diagnostics().rejectedTasks());
            }
            assertEquals(300, executor.diagnostics().completedTasks());
            assertEquals(280, executor.diagnostics().succeededTasks()); assertEquals(20, executor.diagnostics().timedOutTasks());
        } finally { for (var agent : agents) agent.close(); }
    }
}
