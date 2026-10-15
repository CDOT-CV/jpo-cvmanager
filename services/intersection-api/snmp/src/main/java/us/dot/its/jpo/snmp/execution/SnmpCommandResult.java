package us.dot.its.jpo.snmp.execution;

import java.time.Instant;
import java.util.List;
import us.dot.its.jpo.snmp.client.*;

/** Request/response values are available for reconciliation, but excluded from toString and logs. */
public record SnmpCommandResult(int sequence, String operation, Status status, Instant startedAt, Instant finishedAt,
        int submissions, List<SnmpOid> requestedOids, List<SnmpBinding> requestedBindings,
        List<SnmpBinding> response, SnmpFailure failure) {
    public enum Status { RUNNING, NOT_DISPATCHED, SUCCEEDED, REJECTED, READ_TIMED_OUT, WRITE_OUTCOME_UNKNOWN, CANCELLED, FAILED }
    public SnmpCommandResult {
        requestedOids = List.copyOf(requestedOids); requestedBindings = List.copyOf(requestedBindings); response = List.copyOf(response);
    }
    @Override public String toString() { return "SnmpCommandResult[sequence=" + sequence + ", operation=" + operation + ", status=" + status + ", submissions=" + submissions + "]"; }
}
