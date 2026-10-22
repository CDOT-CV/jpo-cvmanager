package us.dot.its.jpo.snmp.snmp4j;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.Integer32;
import us.dot.its.jpo.snmp.client.SnmpValue;
import us.dot.its.jpo.snmp.client.SnmpCredentials;
import us.dot.its.jpo.snmp.client.SnmpRequestPolicy;
import us.dot.its.jpo.snmp.execution.*;
import us.dot.its.jpo.snmp.rsu.ntcip1218.*;
import us.dot.its.jpo.snmp.rsu.ntcip1218.Ntcip1218ForwardingTasks.SnapshotAction;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class Ntcip1218ForwardingTasksTest {
    private static final SnmpCredentials CREDS = new SnmpCredentials("fixture-user", "fixture-auth-pass", "fixture-privacy-pass");
    private final Ntcip1218ForwardingTasks tasks = new Ntcip1218ForwardingTasks(Clock.systemUTC());
    private final ForwardingRequest request = new ForwardingRequest(MessageType.MAP, "198.51.100.3", true);

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
            var task = tasks.createWithSnapshot(device, request, new SnapshotAction("save-snapshot", snapshots::add));
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
            assertEquals(List.of("configure-forwarding", "read-current-forwarding", "save-snapshot"),
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

    @Test void selectedRowDeletionUsesActualTableAndSupportsUnknownPsids() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client)) {
            var device = new Ntcip1218Device(agent.target());
            var helper = new Ntcip1218Client(client, Clock.systemUTC());
            // A manual receive MAP at index 255 must not select the preset transmit MAP row.
            var received = helper.createBindings(new ForwardingRequest(MessageType.BSM, request.destination(), false), 255);
            for (var binding : received) agent.values.put(new OID(binding.oid().value()),
                    binding.value() instanceof SnmpValue.Octets octets
                            ? new OctetString(octets.bytes())
                            : new Integer32(((SnmpValue.IntegerValue) binding.value()).value()));
            agent.integer(ForwardingTable.RECEIVE.cell(10, 255).value(), 1);
            agent.values.put(new OID(ForwardingTable.RECEIVE.cell(2, 255).value()),
                    new OctetString(MessageType.MAP.psid().bytes()));
            run(executor, tasks.create(device, request));
            var snapshots = new ConcurrentLinkedQueue<List<ForwardingRow>>();
            var deleted = run(executor, tasks.deleteWithSnapshot(device, ForwardingTable.RECEIVE, 255,
                    request.destination(), 46800, new SnapshotAction("save", snapshots::add)));
            assertEquals(true, Ntcip1218ForwardingTasks.changed(deleted));
            assertEquals(ForwardingTable.TRANSMIT, snapshots.remove().getFirst().table());
            agent.values.put(new OID(ForwardingTable.TRANSMIT.cell(2, 1).value()),
                    new OctetString(new byte[] { 0x21 }));
            var unknown = run(executor, tasks.deleteWithSnapshot(device, ForwardingTable.TRANSMIT, 1,
                    request.destination(), 44920, new SnapshotAction("save", snapshots::add)));
            assertEquals(true, Ntcip1218ForwardingTasks.changed(unknown));
            assertTrue(snapshots.remove().isEmpty());
            var absent = run(executor, tasks.deleteWithSnapshot(device, ForwardingTable.TRANSMIT, 1,
                    request.destination(), 44920, new SnapshotAction("save", snapshots::add)));
            assertEquals(false, Ntcip1218ForwardingTasks.changed(absent));
        }
    }

    @Test void changedSelectedRowIsNotDeleted() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client)) {
            var device = new Ntcip1218Device(agent.target());
            run(executor, tasks.create(device, request));
            for (var target : List.of("198.51.100.4", request.destination())) {
                int port = target.equals(request.destination()) ? 44921 : 44920;
                var result = run(executor, tasks.deleteWithSnapshot(device, ForwardingTable.TRANSMIT, 1,
                        target, port, new SnapshotAction("save", rows -> fail("No snapshot after a mismatch"))));
                assertEquals(SnmpTaskResult.Status.FAILED, result.status());
                assertEquals(SnmpTaskResult.WriteState.NOT_SUBMITTED, result.writeState());
            }
            assertEquals(1, agent.writes.get());
            assertThrows(IllegalArgumentException.class, () -> tasks.deleteWithSnapshot(device,
                    ForwardingTable.RECEIVE, 256, request.destination(), 46800, new SnapshotAction("save", rows -> {})));
        }
    }

    @Test void defaultProcedureDoesNotReadUnrelatedTablesButExplicitSnapshotRequiresThem() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client)) {
            // An incomplete receive row must not affect a verified MAP transmit change.
            agent.integer(ForwardingTable.RECEIVE.cell(4, 1).value(), 46800);
            var device = new Ntcip1218Device(agent.target());
            var change = run(executor, tasks.create(device, request));
            assertEquals(SnmpTaskResult.Status.SUCCEEDED, change.status());
            assertEquals(SnmpTaskResult.WriteState.VERIFIED, change.writeState());
            assertEquals(1, change.steps().size());
            assertEquals(List.of("WALK", "SET", "GET"), change.steps().getFirst().commands().stream()
                    .map(SnmpCommandResult::operation).toList());
            var callbacks = new AtomicInteger();
            var snapshot = run(executor, tasks.createWithSnapshot(device, request,
                    new SnapshotAction("save-snapshot", rows -> callbacks.incrementAndGet())));
            assertEquals(SnmpTaskResult.Status.FAILED, snapshot.status());
            assertEquals(SnmpTaskResult.WriteState.VERIFIED, snapshot.writeState());
            assertEquals(true, Ntcip1218ForwardingTasks.changed(snapshot));
            assertEquals(SnmpStepResult.Status.FAILED, snapshot.steps().get(1).status());
            assertEquals(SnmpStepResult.Status.NOT_STARTED, snapshot.steps().getLast().status());
            assertEquals(0, callbacks.get());
        }
    }

    @Test void explicitSnapshotWithoutCallbackReturnsBothTables() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client)) {
            var device = new Ntcip1218Device(agent.target());
            var create = run(executor, tasks.createWithSnapshot(device, request));
            assertEquals(SnmpTaskResult.Status.SUCCEEDED, create.status());
            assertEquals(2, create.steps().size());
            assertEquals(1, assertInstanceOf(List.class, create.steps().getLast().value()).size());
            var delete = run(executor, tasks.deleteWithSnapshot(device, MessageType.MAP, request.destination()));
            assertEquals(SnmpTaskResult.Status.SUCCEEDED, delete.status());
            assertTrue(assertInstanceOf(List.class, delete.steps().getLast().value()).isEmpty());
            assertEquals(SnmpTaskResult.WriteState.VERIFIED, delete.writeState());
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
            var result = run(executor, tasks.createWithSnapshot(new Ntcip1218Device(agent.target()), request,
                    new SnapshotAction("save-snapshot", rows -> { throw new IllegalStateException("fixture failure"); })));
            assertEquals(SnmpTaskResult.Status.FAILED, result.status());
            assertEquals(SnmpTaskResult.WriteState.VERIFIED, result.writeState());
            assertEquals(true, Ntcip1218ForwardingTasks.changed(result));
            assertEquals(SnmpStepResult.Status.SUCCEEDED, result.steps().get(1).status());
            assertEquals(SnmpStepResult.Status.FAILED, result.steps().getLast().status());
        }
    }

    @Test void lostWriteNeverRunsFollowUpOrReplaysCreate() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client(); var executor = executor(client)) {
            agent.dropWrites = true;
            var callbacks = new AtomicInteger();
            var result = run(executor, tasks.createWithSnapshot(new Ntcip1218Device(agent.target()), request,
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
