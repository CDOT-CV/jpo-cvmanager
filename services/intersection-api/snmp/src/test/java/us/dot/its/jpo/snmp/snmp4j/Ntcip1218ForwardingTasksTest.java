package us.dot.its.jpo.snmp.snmp4j;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import us.dot.its.jpo.snmp.client.SnmpCredentials;
import us.dot.its.jpo.snmp.client.SnmpRequestPolicy;
import us.dot.its.jpo.snmp.execution.*;
import us.dot.its.jpo.snmp.rsu.ntcip1218.*;
import us.dot.its.jpo.snmp.rsu.ntcip1218.Ntcip1218ForwardingTasks.SnapshotAction;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class Ntcip1218ForwardingTasksTest {
    private static final SnmpCredentials CREDS = new SnmpCredentials("fixture-user", "fixture-auth-pass", "fixture-privacy-pass");
    private final Ntcip1218ForwardingTasks tasks = new Ntcip1218ForwardingTasks(Clock.systemUTC(), ZoneOffset.UTC);
    private final ForwardingRequest request = new ForwardingRequest(MessageType.MAP, "10.235.1.3", true);

    private Snmp4jClient client() {
        return new Snmp4jClient(new Snmp4jOptions(Duration.ofMillis(500), 0, Duration.ofSeconds(5),
                Duration.ofSeconds(1), 2, 4, 100, 100, 8192, 16));
    }
    private SnmpTaskExecutor executor(Snmp4jClient client) {
        return new SnmpTaskExecutor(client, new SnmpExecutorOptions(2, 10, Duration.ofSeconds(1)));
    }
    private SnmpTaskOptions options(int maxRequests) {
        return new SnmpTaskOptions(Duration.ofSeconds(5), Duration.ofSeconds(5),
                new SnmpRequestPolicy(Duration.ofMillis(500), 0, false), maxRequests);
    }
    private SnmpTaskResult run(SnmpTaskExecutor executor, SnmpTask task) throws Exception {
        return executor.submit(task, options(0)).await(Duration.ofSeconds(10));
    }

    @Test void reusableRecipeCreatesIndependentExecutionsThenDeletesAndReportsNoOp() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client)) {
            var device = new Ntcip1218Device(agent.target());
            var snapshots = new ConcurrentLinkedQueue<List<ForwardingRow>>();
            var task = tasks.create(device, request, new SnapshotAction("save-snapshot", snapshots::add));
            assertEquals("create-message-forwarding.MAP.TRANSMIT", task.name());
            assertEquals("delete-message-forwarding.BSM.RECEIVE",
                    tasks.delete(device, MessageType.BSM, request.destination()).name());
            var first = run(executor, task);
            var second = run(executor, task);
            assertNotEquals(first.taskId(), second.taskId());
            assertEquals(SnmpTaskResult.WriteState.VERIFIED, first.writeState());
            assertEquals(SnmpTaskResult.WriteState.VERIFIED, second.writeState());
            assertEquals(true, Ntcip1218ForwardingTasks.changed(first));
            var firstSnapshot = snapshots.remove();
            assertEquals(1, firstSnapshot.size());
            assertEquals(44920, firstSnapshot.getFirst().port());
            assertTrue(firstSnapshot.getFirst().includeSecurity());
            assertThrows(UnsupportedOperationException.class, firstSnapshot::clear);
            assertEquals(2, snapshots.remove().size());
            assertEquals(List.of("WALK", "SET", "GET"), first.steps().getFirst().commands().stream()
                    .map(SnmpCommandResult::operation).toList());
            assertEquals(List.of("configure-forwarding", "confirm-result", "read-current-forwarding", "save-snapshot"),
                    first.steps().stream().map(SnmpStepResult::name).toList());

            assertEquals(true, Ntcip1218ForwardingTasks.changed(run(executor, tasks.delete(device, MessageType.MAP, request.destination()))));
            assertEquals(true, Ntcip1218ForwardingTasks.changed(run(executor, tasks.delete(device, MessageType.MAP, request.destination()))));
            var noOp = run(executor, tasks.delete(device, MessageType.MAP, request.destination()));
            assertEquals(false, Ntcip1218ForwardingTasks.changed(noOp));
            assertEquals(SnmpTaskResult.Status.SUCCEEDED, noOp.status());
            assertEquals(SnmpTaskResult.WriteState.NOT_SUBMITTED, noOp.writeState());
            assertEquals(4, agent.writes.get());
            assertEquals(0, client.diagnostics().pendingRequests());
        }
    }

    @Test void sharedFactoryHandlesConcurrentTargetsWithDifferentCredentials() throws Exception {
        var otherCredentials = new SnmpCredentials("another-user", "another-auth-pass", "another-privacy-pass");
        try (var first = new LoopbackAgent(CREDS); var second = new LoopbackAgent(otherCredentials, 0, 1, "800000000105040302");
                var client = client(); var executor = executor(client)) {
            var left = executor.submit(tasks.create(new Ntcip1218Device(first.target()), request), options(0));
            var right = executor.submit(tasks.create(new Ntcip1218Device(second.target()), request), options(0));
            assertEquals(SnmpTaskResult.Status.SUCCEEDED, left.await(Duration.ofSeconds(10)).status());
            assertEquals(SnmpTaskResult.Status.SUCCEEDED, right.await(Duration.ofSeconds(10)).status());
            assertEquals(1, first.writes.get()); assertEquals(1, second.writes.get());
        }
    }

    @Test void followUpFailureRetainsVerifiedWriteAndSnapshotEvidence() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client)) {
            var result = run(executor, tasks.create(new Ntcip1218Device(agent.target()), request,
                    new SnapshotAction("save-snapshot", rows -> { throw new IllegalStateException("fixture failure"); })));
            assertEquals(SnmpTaskResult.Status.FAILED, result.status());
            assertEquals(SnmpTaskResult.WriteState.VERIFIED, result.writeState());
            assertEquals(true, Ntcip1218ForwardingTasks.changed(result));
            assertEquals(SnmpStepResult.Status.SUCCEEDED, result.steps().get(2).status());
            assertEquals(SnmpStepResult.Status.FAILED, result.steps().getLast().status());
        }
    }

    @Test void lostWriteNeverRunsFollowUpOrReplaysCreate() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client)) {
            agent.dropWrites = true;
            var callbacks = new AtomicInteger();
            var result = run(executor, tasks.create(new Ntcip1218Device(agent.target()), request,
                    new SnapshotAction("save-snapshot", rows -> callbacks.incrementAndGet())));
            assertEquals(SnmpTaskResult.WriteState.UNKNOWN, result.writeState());
            assertNull(Ntcip1218ForwardingTasks.changed(result));
            assertEquals(1, agent.writes.get());
            assertEquals(0, callbacks.get());
            assertEquals(SnmpStepResult.Status.NOT_STARTED, result.steps().getLast().status());
        }
    }

    @Test void recipeHonorsSubmissionLimitBeforeSendingSet() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client)) {
            var result = executor.submit(tasks.create(new Ntcip1218Device(agent.target()), request), options(1))
                    .await(Duration.ofSeconds(10));
            assertEquals(SnmpTaskResult.Status.FAILED, result.status());
            assertEquals(us.dot.its.jpo.snmp.client.SnmpException.Kind.REQUEST_LIMIT, result.failure().kind());
            assertEquals(SnmpTaskResult.WriteState.NOT_SUBMITTED, result.writeState());
            assertEquals(0, agent.writes.get());
        }
    }
}
