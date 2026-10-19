package us.dot.its.jpo.snmp.client;

import java.util.Arrays;
import java.util.Objects;

/** Numeric OIDs keep transport output independent of installed MIB display hints. */
public record SnmpOid(String value) implements Comparable<SnmpOid> {
    public SnmpOid {
        Objects.requireNonNull(value, "OID");
        if (value.startsWith(".")) value = value.substring(1);
        if (!value.matches("[0-2](\\.\\d++)++")) throw new IllegalArgumentException("Invalid numeric OID");
        long[] parts = Arrays.stream(value.split("\\.")).mapToLong(Long::parseLong).toArray();
        if (parts.length > 128 || (parts[0] < 2 && parts[1] > 39)
                || Arrays.stream(parts).anyMatch(n -> n > 0xffffffffL)) {
            throw new IllegalArgumentException("OID component out of range");
        }
        value = String.join(".", Arrays.stream(parts).mapToObj(Long::toString).toList());
    }

    public SnmpOid append(int component) {
        if (component < 0) throw new IllegalArgumentException("Negative OID component");
        return new SnmpOid(value + "." + component);
    }

    public boolean isWithin(SnmpOid root) {
        return value.startsWith(root.value + ".");
    }

    @Override public int compareTo(SnmpOid other) {
        String[] left = value.split("\\.");
        String[] right = other.value.split("\\.");
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            int cmp = Long.compare(Long.parseLong(left[i]), Long.parseLong(right[i]));
            if (cmp != 0) return cmp;
        }
        return Integer.compare(left.length, right.length);
    }

    @Override public String toString() { return value; }
}
