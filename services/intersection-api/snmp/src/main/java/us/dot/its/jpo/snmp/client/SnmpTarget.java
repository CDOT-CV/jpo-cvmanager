package us.dot.its.jpo.snmp.client;

import java.util.Objects;

/** Literal IPv4 only, matching the current API and avoiding implicit DNS/transport aliases. */
public record SnmpTarget(String address, int port, SnmpCredentials credentials) {
    public SnmpTarget {
        validateIpv4(address);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid SNMP port");
        Objects.requireNonNull(credentials);
    }
    public SnmpTarget(String address, SnmpCredentials credentials) { this(address, 161, credentials); }
    public static void validateIpv4(String address) {
        if (address == null || !address.matches("(0|[1-9][0-9]{0,2})(\\.(0|[1-9][0-9]{0,2})){3}")) {
            throw new IllegalArgumentException("Expected literal IPv4 address");
        }
        for (String octet : address.split("\\.")) {
            if (Integer.parseInt(octet) > 255) throw new IllegalArgumentException("Invalid IPv4 octet");
        }
    }
    public String agent() { return "udp:" + address + ":" + port; }
}
