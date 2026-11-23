package us.dot.its.jpo.snmp.client;

/**
 * Evidence returned by a device procedure only after verification or a confirmed no-op.
 * A plain SET response is not enough to create this result. The executor recognizes it when
 * a successful WRITE step verifies its own acknowledged SET.
 *
 * @param <T> immutable procedure-specific result type
 * @param changed true for a verified change; false for a confirmed no-op
 * @param value verified result, or null when the procedure has no returned data
 */
public record SnmpWriteResult<T>(boolean changed, T value) {
    @Override public String toString() { return "SnmpWriteResult[changed=" + changed + "]"; }
}
