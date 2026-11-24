package us.dot.its.jpo.snmp.client;

import java.util.Objects;

/**
 * One numeric OID and its typed request or response value.
 *
 * @param oid object identifier
 * @param value SNMP value, including explicit missing-value markers
 */
public record SnmpBinding(SnmpOid oid, SnmpValue value) {
    public SnmpBinding { Objects.requireNonNull(oid); Objects.requireNonNull(value); }
}
