package us.dot.its.jpo.snmp.client;

/** Safe diagnostic categories; credentials and raw library exceptions are never attached. */
public final class SnmpException extends RuntimeException {
    public enum Kind { AUTHENTICATION, AGENT_REJECTED, TIMEOUT, INVALID_OUTPUT, EXECUTION, OUTPUT_LIMIT, BUSY, CANCELLED, VERIFICATION, REQUEST_LIMIT }
    private final Kind kind;
    private final boolean writeOutcomeUnknown;
    private final Integer agentErrorStatus;
    private final Integer agentErrorIndex;
    public SnmpException(Kind kind, boolean writeOutcomeUnknown) {
        this(kind, writeOutcomeUnknown, null, null);
    }
    public SnmpException(Kind kind, boolean writeOutcomeUnknown, Integer agentErrorStatus, Integer agentErrorIndex) {
        super("SNMP operation failed: " + kind + (writeOutcomeUnknown ? "; write outcome unknown, readback required" : ""));
        this.kind = kind;
        this.writeOutcomeUnknown = writeOutcomeUnknown;
        this.agentErrorStatus = agentErrorStatus;
        this.agentErrorIndex = agentErrorIndex;
    }
    public Kind kind() { return kind; }
    public boolean writeOutcomeUnknown() { return writeOutcomeUnknown; }
    public java.util.OptionalInt agentErrorStatus() {
        return agentErrorStatus == null ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(agentErrorStatus);
    }
    public java.util.OptionalInt agentErrorIndex() {
        return agentErrorIndex == null ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(agentErrorIndex);
    }
    public SnmpException withWriteOutcomeUnknown(boolean unknown) {
        return new SnmpException(kind, unknown, agentErrorStatus, agentErrorIndex);
    }
}
