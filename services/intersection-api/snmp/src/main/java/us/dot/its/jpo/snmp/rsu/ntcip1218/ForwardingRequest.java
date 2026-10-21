package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.util.Objects;
import us.dot.its.jpo.snmp.client.SnmpTarget;

/**
 * Inputs for creating one UDP forwarding row. The message preset selects its PSID, port and table.
 *
 * @param messageType message preset to configure
 * @param destination literal IPv4 forwarding destination
 * @param includeSecurity true to preserve IEEE 1609.2 security headers; false to forward only the payload
 */
public record ForwardingRequest(MessageType messageType, String destination, boolean includeSecurity) {
    /**
     * Validates input without contacting a device.
     * @throws IllegalArgumentException if the destination is not a literal IPv4 address
     */
    public ForwardingRequest {
        Objects.requireNonNull(messageType);
        SnmpTarget.validateIpv4(destination);
    }
}
