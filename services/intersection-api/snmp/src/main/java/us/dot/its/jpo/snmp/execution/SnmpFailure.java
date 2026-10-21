package us.dot.its.jpo.snmp.execution;

import us.dot.its.jpo.snmp.client.SnmpException;

/**
 * Safe failure evidence without raw exception messages or causes.
 *
 * @param kind machine-readable failure category
 * @param writeOutcomeUnknown whether device state must be checked before retrying a write
 * @param agentErrorStatus agent SNMP error status, or null when unavailable
 * @param agentErrorIndex one-based binding index, zero if unspecified, or null when unavailable
 */
public record SnmpFailure(SnmpException.Kind kind, boolean writeOutcomeUnknown, Integer agentErrorStatus, Integer agentErrorIndex) {
    static SnmpFailure from(SnmpException failure) {
        return new SnmpFailure(failure.kind(), failure.writeOutcomeUnknown(),
                failure.agentErrorStatus().isPresent() ? failure.agentErrorStatus().getAsInt() : null,
                failure.agentErrorIndex().isPresent() ? failure.agentErrorIndex().getAsInt() : null);
    }
}
