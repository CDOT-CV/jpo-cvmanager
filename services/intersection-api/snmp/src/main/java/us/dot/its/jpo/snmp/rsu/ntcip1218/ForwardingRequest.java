package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.util.Objects;
import us.dot.its.jpo.snmp.client.SnmpTarget;

/**
 * Inputs for creating one UDP forwarding row. The message preset selects its PSID and table.
 *
 * @param messageType message preset to configure
 * @param destination literal IPv4 forwarding destination
 * @param port destination UDP port, 1024-65535
 * @param includeSecurity true to preserve IEEE 1609.2 security headers; false to forward only the payload
 */
public record ForwardingRequest(MessageType messageType, String destination, int port, boolean includeSecurity) {
    /** Uses the message preset's destination port. */
    public ForwardingRequest(MessageType messageType, String destination, boolean includeSecurity) {
        this(messageType, destination, Objects.requireNonNull(messageType).port(), includeSecurity);
    }

    /**
     * Validates input without contacting a device.
     * @throws IllegalArgumentException if the destination is not a literal IPv4 address or the port is out of range
     */
    public ForwardingRequest {
        Objects.requireNonNull(messageType);
        SnmpTarget.validateIpv4(destination);
        validatePort(port);
    }

    static void validatePort(int port) {
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Invalid forwarding port");
    }
}
