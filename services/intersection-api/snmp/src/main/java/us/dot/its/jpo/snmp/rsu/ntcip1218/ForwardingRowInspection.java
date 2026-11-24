package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import us.dot.its.jpo.snmp.client.SnmpValue;

/**
 * Raw forwarding row for diagnosis and guarded cleanup, including invalid or missing columns.
 * This type is separate from the complete, validated {@link ForwardingRow} snapshot.
 * @param table forwarding table containing the row
 * @param index MIB row index, 1-255
 * @param columns immutable raw column values actually returned by the agent
 */
public record ForwardingRowInspection(ForwardingTable table, int index, Map<Integer, SnmpValue> columns) {
    /**
     * Copies raw values and validates only the table/index, retaining malformed row fields.
     * @throws IllegalArgumentException if the row index is outside 1-255
     */
    public ForwardingRowInspection {
        Objects.requireNonNull(table).cell(2, index);
        columns = Map.copyOf(columns);
    }

    /** Destination text, when its column is an octet string. Missing or wrong types stay empty. */
    public Optional<String> destination() {
        return columns.get(3) instanceof SnmpValue.Octets value ? Optional.of(value.text()) : Optional.empty();
    }

    /** Raw stored port, including out-of-range values; missing or wrong types stay empty. */
    public OptionalInt port() {
        return columns.get(4) instanceof SnmpValue.IntegerValue value ? OptionalInt.of(value.value()) : OptionalInt.empty();
    }

    /** Empty for a valid row, otherwise a validation reason without raw values or credentials. */
    public Optional<String> validationError() {
        try {
            new ForwardingRow(table, index, columns);
            return Optional.empty();
        } catch (IllegalArgumentException failure) {
            return Optional.ofNullable(failure.getMessage()).or(() -> Optional.of("Invalid forwarding row"));
        }
    }
}
