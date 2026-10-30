package us.dot.its.jpo.snmp.client;

import java.util.Objects;

/**
 * A UDP device endpoint and its credentials. Construction validates input but sends no traffic.
 * Only literal IPv4 addresses are accepted; DNS names and transport aliases are not resolved.
 *
 * @param address dotted-decimal IPv4 address without leading-zero octets
 * @param port UDP port, 1-65535
 * @param credentials credentials for this device
 */
public record SnmpTarget(String address, int port, SnmpCredentials credentials) {
    /**
     * Validates the endpoint and requires credentials.
     * @throws IllegalArgumentException if the address or port is invalid
     * @throws NullPointerException if credentials are null
     */
    public SnmpTarget {
        validateIpv4(address);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid SNMP port");
        Objects.requireNonNull(credentials);
    }

    /**
     * Creates a target on the standard SNMP port, 161.
     * @param address literal IPv4 address
     * @param credentials credentials for this device
     * @throws IllegalArgumentException if the address is invalid
     */
    public SnmpTarget(String address, SnmpCredentials credentials) { this(address, 161, credentials); }

    /**
     * Validates an address without DNS lookup or device I/O.
     * @param address address to check
     * @throws IllegalArgumentException if the address is null or is not canonical dotted-decimal IPv4
     */
    public static void validateIpv4(String address) {
        if (address == null || !address.matches("(0|[1-9]\\d{0,2})(\\.(0|[1-9]\\d{0,2})){3}")) {
            throw new IllegalArgumentException("Expected literal IPv4 address");
        }
        for (String octet : address.split("\\.")) {
            if (Integer.parseInt(octet) > 255) throw new IllegalArgumentException("Invalid IPv4 octet");
        }
    }

    /**
     * Returns the credential-free endpoint label used for task serialization and logs.
     * @return a label such as {@code udp:192.0.2.10:161}
     */
    public String agent() { return "udp:" + address + ":" + port; }
}
