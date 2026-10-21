package us.dot.its.jpo.snmp.client;

/**
 * Typed operation failure with safe diagnostics and optional agent error evidence.
 * Use {@link #kind()} for application decisions, rather than parsing the message.
 * A true {@link #writeOutcomeUnknown()} requires device reconciliation before replaying a write.
 * Credentials and raw transport exception causes are not attached.
 */
public final class SnmpException extends RuntimeException {
    /** Failure categories shared by clients, task results and application error handling. */
    public enum Kind { AUTHENTICATION, AGENT_REJECTED, TIMEOUT, INVALID_OUTPUT, EXECUTION, OUTPUT_LIMIT, BUSY, CANCELLED, VERIFICATION, REQUEST_LIMIT }
    private final Kind kind;
    private final boolean writeOutcomeUnknown;
    private final Integer agentErrorStatus;
    private final Integer agentErrorIndex;

    /**
     * Creates a failure without an agent error response.
     * @param kind failure category
     * @param writeOutcomeUnknown whether the write may have happened without verification
     */
    public SnmpException(Kind kind, boolean writeOutcomeUnknown) {
        this(kind, writeOutcomeUnknown, null, null);
    }

    /**
     * Creates a failure with optional SNMP agent response details.
     * @param kind failure category
     * @param writeOutcomeUnknown whether the write may have happened without verification
     * @param agentErrorStatus numeric SNMP error status, or null when unavailable
     * @param agentErrorIndex one-based binding index, zero if unspecified by the agent, or null when unavailable
     */
    public SnmpException(Kind kind, boolean writeOutcomeUnknown, Integer agentErrorStatus, Integer agentErrorIndex) {
        super("SNMP operation failed: " + kind + (writeOutcomeUnknown ? "; write outcome unknown, readback required" : ""));
        this.kind = kind;
        this.writeOutcomeUnknown = writeOutcomeUnknown;
        this.agentErrorStatus = agentErrorStatus;
        this.agentErrorIndex = agentErrorIndex;
    }

    /**
     * Returns the machine-readable failure category.
     * @return failure category
     */
    public Kind kind() { return kind; }

    /**
     * Indicates that device state must be checked before retrying the write.
     * @return true when the write outcome is uncertain
     */
    public boolean writeOutcomeUnknown() { return writeOutcomeUnknown; }

    /**
     * Returns agent-provided SNMP error status when available.
     * @return numeric error status, or empty when no agent error response was received
     */
    public java.util.OptionalInt agentErrorStatus() {
        return agentErrorStatus == null ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(agentErrorStatus);
    }

    /**
     * Returns the agent's one-based binding index; zero means no specific binding was identified.
     * @return agent error index, or empty when no agent error response was received
     */
    public java.util.OptionalInt agentErrorIndex() {
        return agentErrorIndex == null ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(agentErrorIndex);
    }

    /**
     * Copies the failure category and agent details with updated write uncertainty.
     * @param unknown replacement uncertainty flag
     * @return a new failure with the same safe diagnostic evidence
     */
    public SnmpException withWriteOutcomeUnknown(boolean unknown) {
        return new SnmpException(kind, unknown, agentErrorStatus, agentErrorIndex);
    }
}
