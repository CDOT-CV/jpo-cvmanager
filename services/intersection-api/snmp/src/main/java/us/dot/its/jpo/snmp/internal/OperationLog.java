package us.dot.its.jpo.snmp.internal;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import us.dot.its.jpo.snmp.client.SnmpException;
import us.dot.its.jpo.snmp.client.SnmpTarget;

/** Correlation and timing only; task results own execution history and write evidence. */
public final class OperationLog implements AutoCloseable {
    private static final ThreadLocal<OperationLog> CURRENT = new ThreadLocal<>();
    private final OperationLog previous;
    private final Logger logger;
    private final String id, target, operation;
    private final boolean write;
    private final long started = System.nanoTime();
    private int submissions;
    private boolean writeMayHaveOccurred;

    public OperationLog(Logger logger, SnmpTarget target, String operation, boolean write) {
        this(logger, target, operation, write, UUID.randomUUID().toString());
    }

    public OperationLog(Logger logger, SnmpTarget target, String operation, boolean write, String id) {
        this.id = id; this.logger = logger; this.target = target.agent(); this.operation = operation; this.write = write;
        previous = CURRENT.get();
        CURRENT.set(this);
        emit(Level.DEBUG, "Started.", "");
    }

    public void step(String name) { emit(Level.TRACE, "Starting step " + name + ".", ""); }
    public void rowSelected(int index) { emit(Level.TRACE, "Selected row " + index + ".", ""); }

    public void requestSubmitting(String type, int bindings, long timeoutMs, int retries) {
        submissions++;
        if (type.equals("SET")) writeStarting();
        logger.trace("SNMP {}: Sending {} bindings (timeout={}ms, retries={}) [operation={}{} target={} attempt={}]",
                type, bindings, timeoutMs, retries, id, parent(), target, submissions);
    }

    public void event(String event) {
        String message = switch (event) {
            case "request_response_validated" -> "Response confirmed.";
            case "request_submitted" -> "Request submitted.";
            case "read_rediscovery_started" -> "Retrying read with fresh engine discovery.";
            case "read_rediscovery_joined" -> "Using refreshed engine discovery.";
            case "read_discovery_cache_invalidated" -> "Cleared stale engine discovery.";
            case "no_matching_row" -> "No matching row; no change needed.";
            default -> event.replace('_', ' ') + ".";
        };
        emit(Level.TRACE, message, "");
    }

    public void requestFailed(SnmpException failure) { emit(Level.TRACE, describe(failure), ""); }
    public void succeeded() { emit(terminalLevel(write ? Level.INFO : Level.DEBUG), "Completed.", ""); }
    public void writeStarting() { writeMayHaveOccurred = true; }

    public void failed(SnmpException failure) {
        boolean routine = !failure.writeOutcomeUnknown() && switch (failure.kind()) {
            case TIMEOUT, BUSY, CANCELLED -> true;
            default -> false;
        };
        String agent = failure.agentErrorStatus().isPresent()
                ? " agentError=" + failure.agentErrorStatus().getAsInt() + " binding=" + failure.agentErrorIndex().orElse(0) : "";
        emit(terminalLevel(routine ? Level.DEBUG : Level.WARN), describe(failure), agent);
    }

    public void failedLocally() {
        emit(terminalLevel(Level.WARN), writeMayHaveOccurred
                ? "Write not confirmed; check device before retrying." : "Local execution failed.", "");
    }

    private static String describe(SnmpException failure) {
        return failure.writeOutcomeUnknown() ? "Write not confirmed; check device before retrying."
                : SnmpMessages.failure(failure.kind());
    }

    private void emit(Level level, String message, String detail) {
        logger.atLevel(level).log("SNMP {}: {} [operation={}{} target={} elapsed={}ms{}]", operation, message,
                id, parent(), target, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), detail);
    }

    private Level terminalLevel(Level requested) { return previous != null ? Level.DEBUG : requested; }
    private String parent() { return previous == null ? "" : " parent=" + previous.id; }

    @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
}
