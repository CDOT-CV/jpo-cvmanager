package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.util.List;
import java.util.Objects;
import us.dot.its.jpo.snmp.client.SnmpCredentials;
import us.dot.its.jpo.snmp.client.SnmpTarget;

/**
 * A target selected by inventory or the caller for NTCIP 1218 operations.
 * Construction performs no I/O and does not discover or verify the device's MIB support.
 * @param target underlying generic endpoint and credentials
 */
public record Ntcip1218Device(SnmpTarget target) {
    public Ntcip1218Device {
        Objects.requireNonNull(target);
    }

    /**
     * Creates a device on UDP port 161.
     * @param address literal IPv4 address
     * @param credentials device credentials
     * @throws IllegalArgumentException if the address is invalid
     */
    public Ntcip1218Device(String address, SnmpCredentials credentials) {
        this(new SnmpTarget(address, credentials));
    }

    /**
     * Creates a device on an explicit UDP port.
     * @param address literal IPv4 address
     * @param port SNMP port, 1-65535
     * @param credentials device credentials
     * @throws IllegalArgumentException if the address or port is invalid
     */
    public Ntcip1218Device(String address, int port, SnmpCredentials credentials) {
        this(new SnmpTarget(address, port, credentials));
    }

    /**
     * Validates the entire inventory batch without contacting devices. No partial batch is returned.
     * @param targets inventory targets whose profile must be exactly "1218"
     * @return immutable device list in input order
     * @throws IllegalArgumentException if the batch is empty or any profile is unsupported
     */
    public static List<Ntcip1218Device> preflight(List<ProfiledTarget> targets) {
        if (targets.isEmpty()) throw new IllegalArgumentException("Empty SNMP batch");
        return targets.stream().map(t -> {
            if (!"1218".equals(t.profile())) throw new IllegalArgumentException("Unsupported RSU MIB profile; NTCIP 1218 required");
            return new Ntcip1218Device(t.target());
        }).toList();
    }

    /**
     * Inventory input for preflight; creating the pair alone does not validate the profile.
     * @param target generic endpoint with credentials
     * @param profile inventory MIB identifier, expected to be "1218"
     */
    public record ProfiledTarget(SnmpTarget target, String profile) {}
}
