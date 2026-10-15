package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.util.List;
import java.util.Objects;
import us.dot.its.jpo.snmp.client.SnmpCredentials;
import us.dot.its.jpo.snmp.client.SnmpTarget;

/** A target selected for NTCIP 1218 operations; construction performs no device discovery. */
public record Ntcip1218Device(SnmpTarget target) {
    public Ntcip1218Device {
        Objects.requireNonNull(target);
    }
    public Ntcip1218Device(String address, SnmpCredentials credentials) {
        this(new SnmpTarget(address, credentials));
    }
    public Ntcip1218Device(String address, int port, SnmpCredentials credentials) {
        this(new SnmpTarget(address, port, credentials));
    }
    /** Validates inventory profiles for the entire batch before any device I/O. */
    public static List<Ntcip1218Device> preflight(List<ProfiledTarget> targets) {
        if (targets.isEmpty()) throw new IllegalArgumentException("Empty SNMP batch");
        return targets.stream().map(t -> {
            if (!"1218".equals(t.profile())) throw new IllegalArgumentException("Unsupported RSU MIB profile; NTCIP 1218 required");
            return new Ntcip1218Device(t.target());
        }).toList();
    }
    public record ProfiledTarget(SnmpTarget target, String profile) {}
}
