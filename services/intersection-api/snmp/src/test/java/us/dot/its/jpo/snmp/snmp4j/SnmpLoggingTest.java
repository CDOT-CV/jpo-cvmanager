package us.dot.its.jpo.snmp.snmp4j;

import java.time.Duration;
import java.util.List;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import us.dot.its.jpo.snmp.client.*;
import us.dot.its.jpo.snmp.execution.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class SnmpLoggingTest {
    private static final SnmpCredentials CREDS = new SnmpCredentials("sensitive-user", "sensitive-auth-pass", "sensitive-privacy-pass");
    private static final SnmpOid OID = new SnmpOid("1.3.6.1.4.1.1206.4.2.18.12.1.0");
    private static Snmp4jClient client() {
        return new Snmp4jClient(new Snmp4jOptions(Duration.ofMillis(100), 0, Duration.ofSeconds(2),
                Duration.ofMillis(100), 2, 4, 100, 100, 4096));
    }

    @Test void lostWriteLogsUncertaintyWithoutCredentialsValuesOrExceptionDump() throws Exception {
        try (var capture = new Capture(); var agent = new LoopbackAgent(CREDS); var client = client()) {
            agent.dropWrites = true;
            assertThrows(SnmpException.class, () -> client.set(agent.target(),
                    List.of(new SnmpBinding(OID, SnmpValue.Octets.text("sensitive-payload")))));
            assertEquals(1, agent.writes.get());
            var failure = capture.one("operation_failed", "SET");
            assertEquals(Level.WARN, failure.getLevel());
            String message = failure.getFormattedMessage();
            assertTrue(message.contains("Write not confirmed; check device before retrying."));
            assertTrue(message.contains("elapsed="));
            assertTrue(message.contains("target=" + agent.target().agent()));
            assertTrue(capture.messages().stream().anyMatch(line -> line.contains("Sending 1 bindings") && line.contains("retries=0")));
            assertFalse(capture.messages().stream().anyMatch(line -> line.contains(": Completed. ")));
            capture.assertRedacted();
        }
    }

    @Test void naturalReadTimeoutIsDebugAndHasAnExplicitTerminalOutcome() throws Exception {
        try (var capture = new Capture(); var agent = new LoopbackAgent(CREDS); var client = client()) {
            agent.dropReads = true;
            assertThrows(SnmpException.class, () -> client.get(agent.target(), List.of(OID)));
            var failure = capture.one("operation_failed", "GET");
            assertEquals(Level.DEBUG, failure.getLevel());
            assertTrue(failure.getFormattedMessage().contains("Device did not respond."));
            assertFalse(capture.events().stream().anyMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN)));
            capture.assertRedacted();
        }
    }

    @Test void agentRejectionPreservesErrorDetailsInLog() throws Exception {
        try (var capture = new Capture(); var agent = new LoopbackAgent(CREDS); var client = client()) {
            agent.rejectWrites = true;
            assertThrows(SnmpException.class, () -> client.set(agent.target(), List.of(new SnmpBinding(OID, new SnmpValue.IntegerValue(1)))));
            var message = capture.one("operation_failed", "SET").getFormattedMessage();
            assertTrue(message.contains("Device rejected the request."));
            assertTrue(message.contains("agentError=17"));
            assertTrue(message.contains("binding=1"));
        }
    }

    @Test void concurrentEngineRecoveryInvalidatesDiscoveryOnlyOnce() throws Exception {
        try (var capture = new Capture(); var client = client(); var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            int port;
            try (var original = new LoopbackAgent(CREDS)) {
                port = original.port(); original.integer(OID.value(), 1);
                client.get(original.target(), List.of(OID));
            }
            try (var replacement = new LoopbackAgent(CREDS, port, 1, "800000000506070809")) {
                replacement.integer(OID.value(), 2);
                var ready = new java.util.concurrent.CountDownLatch(2);
                var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> workers.submit(() -> {
                    long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                    var count = new java.util.concurrent.atomic.AtomicInteger();
                    var scope = new SnmpCallContext() {
                        @Override public SnmpRequestPolicy policy() { return new SnmpRequestPolicy(Duration.ofMillis(100), 0, true); }
                        @Override public long remainingNanos() { return Math.max(1, deadline - System.nanoTime()); }
                        @Override public void beforeRequest(boolean write) {
                            if (count.incrementAndGet() == 1) {
                                ready.countDown();
                                try { assertTrue(ready.await(1, java.util.concurrent.TimeUnit.SECONDS)); }
                                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
                            }
                        }
                    };
                    assertEquals(new SnmpValue.IntegerValue(2), client.scoped(scope).get(replacement.target(), List.of(OID)).getFirst().value());
                    assertEquals(2, count.get());
                })).toList();
                for (var task : tasks) task.get(3, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(1, capture.messages().stream().filter(message -> message.contains("Retrying read with fresh engine discovery.")).count());
                assertEquals(1, capture.messages().stream().filter(message -> message.contains("Using refreshed engine discovery.")).count());
            }
        }
    }

    @Test void taskIdCorrelatesWorkerCallsAndResultsDoNotPrintValues() throws Exception {
        try (var capture = new Capture(); var agent = new LoopbackAgent(CREDS); var client = client();
                var executor = new SnmpTaskExecutor(client, new SnmpExecutorOptions(1, 10, Duration.ofSeconds(1)))) {
            var handle = executor.submit(new SnmpTask("configure", agent.target(), List.of(
                    new SnmpStep<>("change", SnmpStep.Kind.WRITE, ctx -> ctx.client().set(ctx.target(),
                            List.of(new SnmpBinding(OID, SnmpValue.Octets.text("sensitive-payload"))))))));
            var result = handle.await(Duration.ofSeconds(3));
            assertEquals(SnmpTaskResult.Status.SUCCEEDED, result.status());
            assertEquals(handle.taskId(), field(capture.one("operation_succeeded", "SET").getFormattedMessage(), "parent"));
            assertFalse(result.toString().contains("sensitive-"));
            assertFalse(result.steps().getFirst().toString().contains("sensitive-"));
            assertFalse(result.steps().getFirst().commands().getFirst().toString().contains("sensitive-"));
            var summaryLine = capture.messages().stream().filter(line -> line.startsWith("SNMP configure - ")).findFirst().orElseThrow();
            assertTrue(summaryLine.contains("task=" + handle.taskId()));
            assertTrue(summaryLine.contains("queue=" + result.queueWait().toMillis() + "ms"));
            assertTrue(summaryLine.contains("run=" + result.executionTime().toMillis() + "ms"));
            assertTrue(summaryLine.contains("requests=" + result.submissions()));
            assertTrue(summaryLine.contains("steps=change="));
            capture.assertRedacted();
        }
    }

    @Test void normalLoggingEmitsOneActionableTaskWarningWithoutNestedWarnings() throws Exception {
        for (boolean loseWrite : new boolean[]{true, false}) {
            try (var capture = new Capture(Level.INFO); var agent = new LoopbackAgent(CREDS); var client = client();
                    var executor = new SnmpTaskExecutor(client, new SnmpExecutorOptions(1, 10, Duration.ofSeconds(1)))) {
                agent.dropWrites = loseWrite;
                var task = new SnmpTask("configure", agent.target(), List.of(
                        new SnmpStep<>("change", SnmpStep.Kind.WRITE, ctx -> {
                            var response = ctx.client().set(ctx.target(), List.of(new SnmpBinding(OID, new SnmpValue.IntegerValue(1))));
                            agent.dropReads = true;
                            return response;
                        }),
                        new SnmpStep<>("verify", SnmpStep.Kind.VERIFY, ctx -> {
                            ctx.client().get(ctx.target(), List.of(OID)); return true;
                        })));
                var result = executor.submit(task, new SnmpTaskOptions(Duration.ofSeconds(1), Duration.ofSeconds(2),
                        new SnmpRequestPolicy(Duration.ofMillis(100), 0, false), 0)).await(Duration.ofSeconds(3));
                assertEquals(1, capture.events().size());
                assertEquals(Level.WARN, capture.events().getFirst().getLevel());
                String expected = loseWrite ? "Write not confirmed; check device before retrying." : "Change acknowledged; verification timed out.";
                assertEquals(expected, result.summary().message());
                assertTrue(capture.messages().getFirst().contains(expected));
                assertTrue(capture.messages().getFirst().contains("task=" + result.taskId()));
                assertFalse(capture.messages().getFirst().contains("writeOutcomeUnknown"));
                capture.assertRedacted();
            }
        }
    }

    private static String field(String message, String name) {
        return java.util.Arrays.stream(message.replace("[", "").replace("]", "").split(" ")).filter(part -> part.startsWith(name + "="))
                .findFirst().orElseGet(() -> {
                    if (name.equals("parent")) return "parent=none";
                    throw new AssertionError("Missing field: " + name);
                }).substring(name.length() + 1);
    }

    private static final class Capture implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger("us.dot.its.jpo.snmp");
        private final Level previous = logger.getLevel();
        private final boolean additive = logger.isAdditive();
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        Capture() {
            this(Level.TRACE);
        }
        Capture(Level level) {
            appender.list = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            appender.start();
            logger.addAppender(appender);
            logger.setAdditive(false);
            logger.setLevel(level);
        }
        List<ILoggingEvent> events() { return List.copyOf(appender.list); }
        List<String> messages() { return events().stream().map(ILoggingEvent::getFormattedMessage).toList(); }
        ILoggingEvent one(String event, String operation) {
            var matches = events().stream().filter(item -> {
                String message = item.getFormattedMessage();
                if (!message.startsWith("SNMP " + operation + ": ") || item.getLevel() == Level.TRACE) return false;
                boolean success = message.startsWith("SNMP " + operation + ": Completed.");
                boolean start = message.startsWith("SNMP " + operation + ": Started.");
                return event.equals("operation_succeeded") ? success : !success && !start;
            }).toList();
            assertEquals(1, matches.size(), () -> "Expected one " + event + " for " + operation);
            return matches.getFirst();
        }
        void assertRedacted() {
            for (var event : events()) {
                assertFalse(event.getFormattedMessage().contains("sensitive-"));
                assertFalse(event.getFormattedMessage().contains("198.51.100.20"));
                assertNull(event.getThrowableProxy());
            }
        }
        @Override public void close() {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previous);
            logger.setAdditive(additive);
        }
    }
}
