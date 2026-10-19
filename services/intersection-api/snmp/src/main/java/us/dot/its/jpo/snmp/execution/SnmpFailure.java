package us.dot.its.jpo.snmp.execution;

import us.dot.its.jpo.snmp.client.SnmpException;

/** Safe failure evidence. Deliberately excludes raw exception messages and causes. */
public record SnmpFailure(SnmpException.Kind kind, boolean writeOutcomeUnknown, Integer agentErrorStatus, Integer agentErrorIndex) {
    static SnmpFailure from(SnmpException failure) {
        return new SnmpFailure(failure.kind(), failure.writeOutcomeUnknown(),
                failure.agentErrorStatus().isPresent() ? failure.agentErrorStatus().getAsInt() : null,
                failure.agentErrorIndex().isPresent() ? failure.agentErrorIndex().getAsInt() : null);
    }
}
