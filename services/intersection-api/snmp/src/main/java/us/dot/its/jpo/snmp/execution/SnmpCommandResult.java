package us.dot.its.jpo.snmp.execution;

import java.time.Instant;
import java.util.List;
import us.dot.its.jpo.snmp.client.*;

/**
 * Evidence for one logical client call. A WALK may contain multiple adapter submissions.
 * Request and response values are retained for reconciliation but excluded from toString and logs.
 *
 * @param finishedAt null while the call is running
 * @param submissions adapter sends for this call, excluding internal discovery and protocol retries
 * @param requestedBindings SET payload, empty for reads
 * @param response validated response, empty when the call did not return successfully
 * @param failure safe failure evidence, or null when none was recorded
 */
public record SnmpCommandResult(int sequence, String operation, Status status, Instant startedAt, Instant finishedAt,
        int submissions, List<SnmpOid> requestedOids, List<SnmpBinding> requestedBindings,
        List<SnmpBinding> response, SnmpFailure failure) {
    /** Call outcomes; NOT_DISPATCHED means no adapter submission was admitted. */
    public enum Status { RUNNING, NOT_DISPATCHED, SUCCEEDED, REJECTED, READ_TIMED_OUT, WRITE_OUTCOME_UNKNOWN, CANCELLED, FAILED }
    public SnmpCommandResult {
        requestedOids = List.copyOf(requestedOids); requestedBindings = List.copyOf(requestedBindings); response = List.copyOf(response);
    }
    @Override public String toString() { return "SnmpCommandResult[sequence=" + sequence + ", operation=" + operation + ", status=" + status + ", submissions=" + submissions + "]"; }
}
