package us.dot.its.jpo.snmp.client;

import java.util.Objects;

public record SnmpBinding(SnmpOid oid, SnmpValue value) {
    public SnmpBinding { Objects.requireNonNull(oid); Objects.requireNonNull(value); }
}
