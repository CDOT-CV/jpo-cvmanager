package us.dot.its.jpo.snmp.internal;

import us.dot.its.jpo.snmp.client.SnmpException;

/** Stable, credential-free descriptions shared by compact results and logs. */
public final class SnmpMessages {
    private SnmpMessages() {}
    public static String failure(SnmpException.Kind kind) {
        return switch (kind) {
            case TIMEOUT -> "Device did not respond.";
            case CANCELLED -> "Cancelled.";
            case AUTHENTICATION -> "SNMP authentication failed.";
            case AGENT_REJECTED -> "Device rejected the request.";
            case INVALID_OUTPUT -> "Device returned an invalid response.";
            case BUSY -> "Local SNMP capacity unavailable.";
            case REQUEST_LIMIT -> "Request limit reached.";
            case OUTPUT_LIMIT -> "Result limit reached.";
            case VERIFICATION -> "Readback did not match the requested change.";
            case EXECUTION -> "Task failed.";
        };
    }
}
