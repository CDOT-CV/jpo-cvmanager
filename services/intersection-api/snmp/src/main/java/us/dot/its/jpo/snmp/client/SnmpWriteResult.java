package us.dot.its.jpo.snmp.client;

/** Returned only after a device procedure verifies its change, or confirms a no-op. */
public record SnmpWriteResult<T>(boolean changed, T value) {
    @Override public String toString() { return "SnmpWriteResult[changed=" + changed + "]"; }
}
