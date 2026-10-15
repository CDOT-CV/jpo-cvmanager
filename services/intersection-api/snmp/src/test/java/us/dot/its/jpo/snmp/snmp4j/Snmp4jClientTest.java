package us.dot.its.jpo.snmp.snmp4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.snmp4j.PDU;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.Opaque;
import us.dot.its.jpo.snmp.client.*;
import us.dot.its.jpo.snmp.rsu.ntcip1218.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class Snmp4jClientTest {
    private static final String ROOT = "1.3.6.1.4.1.1206.4.2.18.";
    private static final SnmpOid FIRST = new SnmpOid(ROOT + "12.1.0");
    private static final SnmpOid SECOND = new SnmpOid(ROOT + "12.2.0");
    private static final SnmpCredentials CREDS = new SnmpCredentials("fixture-user", "fixture-auth-pass", "fixture-privacy-pass");
    private static Snmp4jOptions options(int concurrency, int sessions, int targets) {
        return new Snmp4jOptions(Duration.ofMillis(300), 0, Duration.ofSeconds(3), Duration.ofSeconds(1), concurrency, sessions, targets, 100, 4096);
    }
    private static Snmp4jClient client() { return new Snmp4jClient(options(8, 8, 100)); }

    @Test void realV3RequestsReuseSessionAndPreserveTypedMissingValues() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client()) {
            agent.integer(FIRST.value(), 123); agent.integer(SECOND.value(), -1);
            var results = client.get(agent.target(), List.of(FIRST, SECOND));
            assertEquals(new SnmpValue.IntegerValue(123), results.getFirst().value());
            assertEquals(new SnmpValue.IntegerValue(-1), results.get(1).value());
            assertEquals(SECOND, client.getNext(agent.target(), List.of(FIRST)).getFirst().oid());
            assertEquals(2, client.walk(agent.target(), new SnmpOid(ROOT + "12")).size());
            assertTrue(client.walk(agent.target(), new SnmpOid(ROOT + "13")).isEmpty());
            var missing = client.get(agent.target(), List.of(new SnmpOid(ROOT + "99.0"))).getFirst().value();
            assertEquals(new SnmpValue.ExceptionValue(SnmpValue.ExceptionValue.Kind.NO_SUCH_INSTANCE), missing);
            assertEquals(1, client.diagnostics().cachedSessions());
            assertEquals(0, client.diagnostics().pendingRequests());
        }
    }

    @Test void mixedCredentialsWithSameUsernameCannotReplaceEachOtherOrGlobalUsm() throws Exception {
        var global = SecurityModels.getInstance().getSecurityModel(new Integer32(3));
        var different = new SnmpCredentials("fixture-user", "different-auth-pass", "different-privacy-pass");
        try (var first = new LoopbackAgent(CREDS); var second = new LoopbackAgent(different); var client = client()) {
            first.integer(FIRST.value(), 100); second.integer(FIRST.value(), 200);
            try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = java.util.stream.IntStream.range(0, 100).mapToObj(i -> workers.submit(() -> {
                    boolean even = i % 2 == 0;
                    var result = client.get(even ? first.target() : second.target(), List.of(FIRST));
                    assertEquals(new SnmpValue.IntegerValue(even ? 100 : 200), result.getFirst().value());
                })).toList();
                for (var future : futures) future.get(15, TimeUnit.SECONDS);
            }
            assertEquals(100, client.diagnostics().completedOperations());
            assertEquals(2, client.diagnostics().cachedSessions());
            assertEquals(0, client.diagnostics().pendingRequests());
            assertSame(global, SecurityModels.getInstance().getSecurityModel(new Integer32(3)));
        }
    }

    @Test void arbitraryUsernamesFromIndependentTargetsRemainIndependent() throws Exception {
        var otherUser = new SnmpCredentials("another-operator", "another-auth-pass", "another-privacy-pass");
        try (var first = new LoopbackAgent(CREDS); var second = new LoopbackAgent(otherUser); var client = client();
                var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            first.integer(FIRST.value(), 10); second.integer(FIRST.value(), 20);
            var tasks = java.util.stream.IntStream.range(0, 50).mapToObj(i -> workers.submit(() -> {
                var agent = i % 2 == 0 ? first : second;
                assertEquals(new SnmpValue.IntegerValue(i % 2 == 0 ? 10 : 20),
                        client.get(agent.target(), List.of(FIRST)).getFirst().value());
            })).toList();
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
            assertEquals(2, client.diagnostics().cachedSessions());
        }
    }

    @Test void identicalCredentialProfilesShareTransportWithIndependentPeerEngineState() throws Exception {
        try (var first = new LoopbackAgent(CREDS, 0, 1, "800000000102030405");
                var second = new LoopbackAgent(CREDS, 0, 7, "800000000506070809");
                var client = client(); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            first.integer(FIRST.value(), 10); second.integer(FIRST.value(), 20);
            var tasks = java.util.stream.IntStream.range(0, 100).mapToObj(i -> workers.submit(() -> {
                var agent = i % 2 == 0 ? first : second;
                assertEquals(new SnmpValue.IntegerValue(i % 2 == 0 ? 10 : 20),
                        client.get(agent.target(), List.of(FIRST)).getFirst().value());
            })).toList();
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
            assertEquals(1, client.diagnostics().cachedSessions());
            assertEquals(100, client.diagnostics().completedOperations());
            assertEquals(0, client.diagnostics().pendingRequests());
        }
    }

    @Test void rebootAndEngineReplacementRecoverUsingExistingClient() throws Exception {
        try (var client = client()) {
            int port;
            try (var agent = new LoopbackAgent(CREDS)) {
                port = agent.port(); agent.integer(FIRST.value(), 10);
                assertEquals(new SnmpValue.IntegerValue(10), client.get(agent.target(), List.of(FIRST)).getFirst().value());
            }
            try (var rebooted = new LoopbackAgent(CREDS, port, 2, "800000000102030405")) {
                rebooted.integer(FIRST.value(), 20);
                assertEquals(new SnmpValue.IntegerValue(20), client.get(rebooted.target(), List.of(FIRST)).getFirst().value());
            }
            try (var replaced = new LoopbackAgent(CREDS, port, 1, "800000000506070809")) {
                replaced.integer(FIRST.value(), 30);
                assertEquals(new SnmpValue.IntegerValue(30), client.get(replaced.target(), List.of(FIRST)).getFirst().value());
            }
            assertEquals(1, client.diagnostics().cachedSessions());
        }
    }

    @Test void busyQueueAndBusySessionFailExplicitlyWithoutDisturbingExistingRequest() throws Exception {
        var settings = new Snmp4jOptions(Duration.ofSeconds(2), 0, Duration.ofSeconds(3), Duration.ofMillis(50), 1, 1, 100, 100, 4096);
        try (var agent = new LoopbackAgent(CREDS); var client = new Snmp4jClient(settings);
                var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            agent.integer(FIRST.value(), 1); client.get(agent.target(), List.of(FIRST)); agent.hold = true;
            var held = workers.submit(() -> client.get(agent.target(), List.of(FIRST)));
            await(() -> agent.active.get() == 1);
            try {
                var failure = assertThrows(SnmpException.class, () -> client.set(agent.target(), List.of(new SnmpBinding(FIRST, new SnmpValue.IntegerValue(2)))));
                assertEquals(SnmpException.Kind.BUSY, failure.kind()); assertFalse(failure.writeOutcomeUnknown());
                assertEquals(0, agent.writes.get()); assertFalse(held.isDone());
            } finally { agent.release(); }
            held.get(3, TimeUnit.SECONDS);
        }
        settings = new Snmp4jOptions(Duration.ofSeconds(2), 0, Duration.ofSeconds(3), Duration.ofMillis(50), 2, 1, 100, 100, 4096);
        try (var agent = new LoopbackAgent(CREDS); var client = new Snmp4jClient(settings);
                var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            agent.integer(FIRST.value(), 1); client.get(agent.target(), List.of(FIRST)); agent.hold = true;
            var held = workers.submit(() -> client.get(agent.target(), List.of(FIRST)));
            await(() -> agent.active.get() == 1);
            try {
                var other = new SnmpTarget("127.0.0.1", agent.port(), new SnmpCredentials("other-user", "other-auth-pass", "other-privacy-pass"));
                assertEquals(SnmpException.Kind.BUSY, assertThrows(SnmpException.class, () -> client.get(other, List.of(FIRST))).kind());
            } finally { agent.release(); }
            held.get(3, TimeUnit.SECONDS);
        }
    }

    @Test void clientShutdownCancelsAnOutstandingWriteAndReleasesResources() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client();
                var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            agent.integer(FIRST.value(), 1); client.get(agent.target(), List.of(FIRST)); agent.hold = true;
            var held = workers.submit(() -> client.set(agent.target(), List.of(new SnmpBinding(FIRST, new SnmpValue.IntegerValue(2)))));
            await(() -> agent.writes.get() == 1);
            client.close();
            var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> held.get(3, TimeUnit.SECONDS));
            assertTrue(((SnmpException) failure.getCause()).writeOutcomeUnknown());
            assertEquals(0, client.diagnostics().pendingRequests());
            assertEquals(0, client.diagnostics().cachedSessions());
        }
    }

    @Test void walkDeadlineAndLimitsNeverReturnPartialSuccess() throws Exception {
        var settings = new Snmp4jOptions(Duration.ofSeconds(1), 0, Duration.ofMillis(200), Duration.ofMillis(50), 2, 2, 100, 100, 4096);
        try (var agent = new LoopbackAgent(CREDS); var client = new Snmp4jClient(settings)) {
            for (int i = 1; i <= 10; i++) agent.integer(ROOT + "12." + i, i);
            client.get(agent.target(), List.of(new SnmpOid(ROOT + "12.1")));
            agent.delayMillis = 70;
            assertEquals(SnmpException.Kind.TIMEOUT, assertThrows(SnmpException.class, () -> client.walk(agent.target(), new SnmpOid(ROOT + "12"))).kind());
            assertEquals(0, client.diagnostics().pendingRequests());
        }
        settings = new Snmp4jOptions(Duration.ofSeconds(1), 0, Duration.ofSeconds(3), Duration.ofMillis(50), 2, 2, 100, 2, 1024);
        try (var agent = new LoopbackAgent(CREDS); var client = new Snmp4jClient(settings)) {
            for (int i = 1; i <= 3; i++) agent.integer(ROOT + "12." + i, i);
            assertEquals(SnmpException.Kind.OUTPUT_LIMIT, assertThrows(SnmpException.class, () -> client.walk(agent.target(), new SnmpOid(ROOT + "12"))).kind());
            agent.alterResponse = pdu -> pdu.get(0).setOid(new OID(ROOT + "12"));
            assertEquals(SnmpException.Kind.INVALID_OUTPUT, assertThrows(SnmpException.class, () -> client.walk(agent.target(), new SnmpOid(ROOT + "12"))).kind());
        }
    }

    @Test void credentialFallbackAlgorithmsAndTwoIndependentClients() throws Exception {
        for (SnmpCredentials.Authentication algorithm : SnmpCredentials.Authentication.values()) {
            var credentials = new SnmpCredentials("fixture-user", "fixture-auth-pass", "", algorithm, SnmpCredentials.Privacy.AES);
            assertEquals(credentials.authenticationPassword(), credentials.privacyPassword());
            try (var agent = new LoopbackAgent(credentials); var first = client(); var second = client()) {
                agent.integer(FIRST.value(), 42);
                assertEquals(first.get(agent.target(), List.of(FIRST)), second.get(agent.target(), List.of(FIRST)));
            }
        }
    }

    @Test void badCredentialsFailWithoutLeakingPasswords() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client()) {
            var target = new SnmpTarget("127.0.0.1", agent.port(), new SnmpCredentials("fixture-user", "wrong-auth-pass", "wrong-privacy-pass"));
            var failure = assertThrows(SnmpException.class, () -> client.get(target, List.of(FIRST)));
            assertFalse(failure.toString().contains("wrong-auth"));
            assertFalse(target.toString().contains("wrong-auth"));
            assertEquals(0, client.diagnostics().pendingRequests());
        }
    }

    @Test void validatesAgentErrorsWrongOidsTypesAndPartialResponses() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client()) {
            agent.integer(FIRST.value(), 1);
            agent.rejectWrites = true;
            var failure = assertThrows(SnmpException.class, () -> client.set(agent.target(), List.of(new SnmpBinding(FIRST, new SnmpValue.IntegerValue(2)))));
            assertEquals(SnmpException.Kind.AGENT_REJECTED, failure.kind());
            assertFalse(failure.writeOutcomeUnknown());
            assertEquals(PDU.notWritable, failure.agentErrorStatus().orElseThrow());
            assertEquals(1, failure.agentErrorIndex().orElseThrow());
            for (var alter : List.<java.util.function.Consumer<PDU>>of(
                    pdu -> pdu.get(0).setOid(new OID(SECOND.value())),
                    pdu -> pdu.get(0).setVariable(new Opaque(new byte[]{1, 2})),
                    pdu -> pdu.remove(0))) {
                agent.alterResponse = alter;
                assertEquals(SnmpException.Kind.INVALID_OUTPUT, assertThrows(SnmpException.class,
                        () -> client.get(agent.target(), List.of(FIRST))).kind());
            }
        }
    }

    @Test void failedCommitOrUndoRequiresReadback() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client()) {
            for (int status : new int[]{PDU.commitFailed, PDU.undoFailed, PDU.genErr}) {
                agent.alterResponse = pdu -> { pdu.setErrorStatus(status); pdu.setErrorIndex(1); };
                var failure = assertThrows(SnmpException.class, () -> client.set(agent.target(), List.of(new SnmpBinding(FIRST, new SnmpValue.IntegerValue(2)))));
                assertTrue(failure.writeOutcomeUnknown());
                assertEquals(status, failure.agentErrorStatus().orElseThrow());
            }
        }
    }

    @Test void lostSetReplyIsUnknownAndNeverReplayed() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client()) {
            agent.dropWrites = true;
            var failure = assertThrows(SnmpException.class, () -> client.set(agent.target(), List.of(new SnmpBinding(FIRST, new SnmpValue.IntegerValue(9)))));
            assertEquals(SnmpException.Kind.TIMEOUT, failure.kind());
            assertTrue(failure.writeOutcomeUnknown());
            assertEquals(1, agent.writes.get());
            assertEquals(new SnmpValue.IntegerValue(9), client.get(agent.target(), List.of(FIRST)).getFirst().value());
            assertEquals(0, client.diagnostics().pendingRequests());
        }
    }

    @Test void requestsRemainIncompleteUntilActualResponseAndAdmissionIsBounded() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = new Snmp4jClient(options(2, 4, 100));
                var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            agent.integer(FIRST.value(), 1);
            client.get(agent.target(), List.of(FIRST)); // Warm discovery before holding responses.
            agent.hold = true;
            var one = workers.submit(() -> client.get(agent.target(), List.of(FIRST)));
            var two = workers.submit(() -> client.get(agent.target(), List.of(FIRST)));
            await(() -> agent.active.get() == 2);
            assertFalse(one.isDone()); assertFalse(two.isDone());
            assertEquals(2, client.diagnostics().activeOperations());
            agent.release();
            one.get(3, TimeUnit.SECONDS); two.get(3, TimeUnit.SECONDS);
            assertTrue(agent.maximum.get() <= 2);
            assertEquals(3, client.diagnostics().completedOperations());
        }
    }

    @Test void interruptionCancelsOutstandingRequest() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var client = client()) {
            agent.integer(FIRST.value(), 1); client.get(agent.target(), List.of(FIRST));
            agent.hold = true;
            var error = new AtomicReference<SnmpException>();
            var finished = new CountDownLatch(1);
            Thread worker = Thread.ofVirtual().start(() -> {
                try { client.get(agent.target(), List.of(FIRST)); }
                catch (SnmpException failure) { error.set(failure); assertTrue(Thread.currentThread().isInterrupted()); }
                finally { finished.countDown(); }
            });
            await(() -> agent.active.get() == 1);
            worker.interrupt();
            assertTrue(finished.await(3, TimeUnit.SECONDS));
            assertEquals(SnmpException.Kind.CANCELLED, error.get().kind());
            assertEquals(0, client.diagnostics().pendingRequests());
        }
    }

    @Test void sessionCapacityAndTargetRotationAreBounded() throws Exception {
        var other = new SnmpCredentials("fixture-user", "other-auth-pass", "other-privacy-pass");
        try (var first = new LoopbackAgent(CREDS); var second = new LoopbackAgent(other);
                var third = new LoopbackAgent(CREDS); var client = new Snmp4jClient(options(2, 1, 1))) {
            for (var agent : List.of(first, second, third, first)) {
                agent.integer(FIRST.value(), 1);
                client.get(agent.target(), List.of(FIRST));
                assertEquals(1, client.diagnostics().cachedSessions());
            }
            client.close(); client.close();
            assertEquals(0, client.diagnostics().cachedSessions());
            assertEquals(SnmpException.Kind.CANCELLED, assertThrows(SnmpException.class,
                    () -> client.get(first.target(), List.of(FIRST))).kind());
        }
    }

    @Test void realNtcipForwardingLifecyclePreservesGoldenProcedures() throws Exception {
        try (var agent = new LoopbackAgent(CREDS); var transport = client()) {
            var ntcip = new Ntcip1218Client(transport, Clock.fixed(Instant.parse("2024-01-30T12:34:00Z"), ZoneOffset.UTC), ZoneOffset.ofHours(-7));
            var device = new Ntcip1218Device(agent.target());
            for (MessageType message : MessageType.values()) {
                var row = ntcip.addForwarding(device, new ForwardingRequest(message, "198.51.100.20", true));
                assertEquals(message.psid(), row.psid()); assertTrue(row.includeSecurity());
                assertTrue(ntcip.deleteForwarding(device, message, "198.51.100.20"));
            }
            assertTrue(ntcip.readForwarding(device).isEmpty());
            assertEquals(12, agent.writes.get());
        }
    }

    static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), "Condition not reached within three seconds");
    }
}
