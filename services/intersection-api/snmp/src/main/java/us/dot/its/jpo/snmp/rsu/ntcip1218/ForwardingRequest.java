package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.util.Objects;
import us.dot.its.jpo.snmp.client.SnmpTarget;

/** includeSecurity preserves IEEE 1609.2 security headers in forwarded messages. */
public record ForwardingRequest(MessageType messageType, String destination, boolean includeSecurity) {
    public ForwardingRequest {
        Objects.requireNonNull(messageType);
        SnmpTarget.validateIpv4(destination);
    }
}
