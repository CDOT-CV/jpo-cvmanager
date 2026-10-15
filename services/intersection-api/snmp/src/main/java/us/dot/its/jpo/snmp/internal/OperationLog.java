package us.dot.its.jpo.snmp.internal;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import us.dot.its.jpo.snmp.client.SnmpException;
import us.dot.its.jpo.snmp.client.SnmpTarget;

/** Internal synchronous-call correlation scope. Never accepts credentials, values or exception text. */
public final class OperationLog implements AutoCloseable {
    private static final ThreadLocal<OperationLog> CURRENT = new ThreadLocal<>();
    private final OperationLog previous;
    private final Logger logger;
    private final String id;
    private final String parentId;
    private final String target;
    private final String operation;
    private final boolean write;
    private final boolean taskRoot;
    private final long started = System.nanoTime();
    private String step = "admission";
    private String lastConfirmedStep = "none";
    private int submissions;
    private Integer rowIndex;
    private boolean writeMayHaveOccurred;

    public OperationLog(Logger logger, SnmpTarget target, String operation, boolean write) {
        this(logger, target, operation, write, UUID.randomUUID().toString(), false);
    }

    public OperationLog(Logger logger, SnmpTarget target, String operation, boolean write, String id) {
        this(logger, target, operation, write, id, true);
    }

    private OperationLog(Logger logger, SnmpTarget target, String operation, boolean write, String id, boolean taskRoot) {
        this.id = id;
        this.logger = logger;
        this.target = target.agent();
        this.operation = operation;
        this.write = write;
        this.taskRoot = taskRoot;
        previous = CURRENT.get();
        parentId = previous == null ? "none" : previous.id;
        CURRENT.set(this);
        emit(Level.DEBUG, "operation_started", "none", false, null, null);
    }

    public void step(String name) {
        step = name;
        event("step_started");
    }

    public void stepSucceeded() {
        lastConfirmedStep = step;
        event("step_succeeded");
    }

    public void rowSelected(int index) {
        rowIndex = index;
        event("row_selected");
    }

    public void requestSubmitting(String type, int bindings, long timeoutMs, int retries) {
        submissions++;
        if (type.equals("SET")) writeStarting();
        // This is entry into SNMP4J, not proof that a datagram reached the device.
        logger.trace("SNMP {}: Sending {} bindings (timeout={}ms, retries={}) [operation={}{} target={} attempt={}]",
                type, bindings, timeoutMs, retries, id, parent(), target, submissions);
    }

    public void event(String event) {
        emit(Level.TRACE, event, "none", false, null, null);
    }

    public void requestFailed(SnmpException failure) {
        emit(Level.TRACE, "request_failed", failure.kind().name(), failure.writeOutcomeUnknown(), null, null);
    }

    public void succeeded() {
        emit(terminalLevel(write ? Level.INFO : Level.DEBUG), "operation_succeeded", "none", false, null, null);
    }

    public void writeStarting() {
        writeMayHaveOccurred = true;
    }

    public void failed(SnmpException failure) {
        boolean routine = !failure.writeOutcomeUnknown() && switch (failure.kind()) {
            case TIMEOUT, BUSY, CANCELLED -> true;
            default -> false;
        };
        emit(terminalLevel(routine ? Level.DEBUG : Level.WARN), "operation_failed", failure.kind().name(),
                failure.writeOutcomeUnknown(), failure.agentErrorStatus().isPresent() ? failure.agentErrorStatus().getAsInt() : null,
                failure.agentErrorIndex().isPresent() ? failure.agentErrorIndex().getAsInt() : null);
    }

    public void failedLocally() {
        emit(terminalLevel(Level.WARN), "operation_failed", "LOCAL_FAILURE", writeMayHaveOccurred, null, null);
    }

    private void emit(Level level, String event, String reason, boolean unknown, Integer status, Integer index) {
        boolean failed = event.equals("operation_failed") || event.equals("request_failed");
        String message = switch (event) {
            case "operation_started" -> "Started.";
            case "operation_succeeded" -> "Completed.";
            case "step_started" -> "Starting step " + step + ".";
            case "step_succeeded" -> "Completed step " + step + ".";
            case "row_selected" -> "Selected row " + rowIndex + ".";
            case "request_response_validated" -> "Response confirmed.";
            case "request_submitted" -> "Request submitted.";
            case "read_rediscovery_started" -> "Retrying read with fresh engine discovery.";
            case "read_rediscovery_joined" -> "Using refreshed engine discovery.";
            case "read_discovery_cache_invalidated" -> "Cleared stale engine discovery.";
            case "no_matching_row" -> "No matching row; no change needed.";
            case "operation_failed", "request_failed" -> describeFailure(reason, unknown);
            default -> event.replace('_', ' ') + ".";
        };
        String detail = (failed ? " step=" + step + (lastConfirmedStep.equals("none") ? "" : " lastConfirmed=" + lastConfirmedStep) : "")
                + (rowIndex == null ? "" : " row=" + rowIndex)
                + (status == null ? "" : " agentError=" + status + " binding=" + index);
        logger.atLevel(level).log("SNMP {}: {} [operation={}{} target={} elapsed={}ms{}]", operation, message,
                id, parent(), target, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), detail);
    }

    private String describeFailure(String reason, boolean unknown) {
        if (reason.equals("VERIFICATION")) return SnmpMessages.failure(SnmpException.Kind.VERIFICATION);
        if ((lastConfirmedStep.equals("create_row") || lastConfirmedStep.equals("delete_row")) && step.startsWith("verify")) {
            return reason.equals("TIMEOUT") ? "Change acknowledged; verification timed out." : "Change acknowledged; verification incomplete.";
        }
        if (unknown) return "Write not confirmed; check device before retrying.";
        return reason.equals("LOCAL_FAILURE") ? "Local execution failed." : SnmpMessages.failure(SnmpException.Kind.valueOf(reason));
    }

    private Level terminalLevel(Level requested) { return previous != null || taskRoot ? Level.DEBUG : requested; }
    private String parent() { return parentId.equals("none") ? "" : " parent=" + parentId; }

    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
