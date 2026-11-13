package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import us.dot.its.jpo.snmp.client.SnmpValue;

/**
 * A complete forwarding row with the MIB's column types and value ranges checked.
 * Unknown PSIDs remain visible so an application can preserve configurations it did not create.
 * Dates are UTC calendar fields, not machine-local times.
 *
 * @param table layout used to interpret column numbers
 * @param index device row index, 1-255
 * @param columns complete column-number/value map, copied into immutable storage
 */
public record ForwardingRow(ForwardingTable table, int index, Map<Integer, SnmpValue> columns) {
    /**
     * Validates completeness, column types, value ranges and date fields.
     * @throws IllegalArgumentException if the row is incomplete or contains invalid values
     */
    public ForwardingRow {
        Objects.requireNonNull(table);
        columns = Map.copyOf(columns);
        table.cell(2, index);

        // Reject incomplete snapshots before a caller can use them to replace stored device state.
        if (columns.size() != table.lastColumn() - 1) throw new IllegalArgumentException("Incomplete forwarding row");
        for (int column = 2; column <= table.lastColumn(); column++) {
            boolean octets = column == 2 || column == 3 || column == table.startColumn() || column == table.stopColumn();
            if (octets ? !(columns.get(column) instanceof SnmpValue.Octets) : !(columns.get(column) instanceof SnmpValue.IntegerValue)) {
                throw new IllegalArgumentException("Invalid forwarding column type");
            }
        }

        var psid = (SnmpValue.Octets) columns.get(2);
        if (psid.bytes().length < 1 || psid.bytes().length > 4) throw new IllegalArgumentException("Invalid PSID");
        int port = ((SnmpValue.IntegerValue) columns.get(4)).value();
        int protocol = ((SnmpValue.IntegerValue) columns.get(5)).value();
        int status = ((SnmpValue.IntegerValue) columns.get(table.statusColumn())).value();
        int security = ((SnmpValue.IntegerValue) columns.get(table.securityColumn())).value();
        if (port < 1024 || port > 65535 || protocol < 1 || protocol > 2 || status < 1 || status > 6
                || security < 0 || security > 1 || ((SnmpValue.Octets) columns.get(3)).bytes().length > 64) {
            throw new IllegalArgumentException("Forwarding value out of range");
        }

        if (table == ForwardingTable.RECEIVE) validateReceiveColumns(columns);
        NtcipDateAndTime.decode((SnmpValue.Octets) columns.get(table.startColumn()));
        NtcipDateAndTime.decode((SnmpValue.Octets) columns.get(table.stopColumn()));
    }

    public SnmpValue.Octets psid() { return (SnmpValue.Octets) columns.get(2); }

    /** Returns destination text as reported by the device; existing rows are not restricted to IPv4. */
    public String destination() { return ((SnmpValue.Octets) columns.get(3)).text(); }
    public int port() { return integer(4); }

    /** Returns the raw RowStatus value; 1 means active. */
    public int status() { return integer(table.statusColumn()); }

    /** Reports whether this row forwards IEEE 1609.2 security headers. */
    public boolean includeSecurity() { return integer(table.securityColumn()) == 1; }

    /** Returns start calendar fields in UTC; interpret with ZoneOffset.UTC, not the local timezone. */
    public LocalDateTime start() { return NtcipDateAndTime.decode((SnmpValue.Octets) columns.get(table.startColumn())); }

    /** Returns stop calendar fields in UTC; interpret with ZoneOffset.UTC, not the local timezone. */
    public LocalDateTime stop() { return NtcipDateAndTime.decode((SnmpValue.Octets) columns.get(table.stopColumn())); }

    // Transmit rows have a different layout and do not contain these receive-only filters.
    private static void validateReceiveColumns(Map<Integer, SnmpValue> columns) {
        int rssi = ((SnmpValue.IntegerValue) columns.get(6)).value();
        int interval = ((SnmpValue.IntegerValue) columns.get(7)).value();
        int authInterval = ((SnmpValue.IntegerValue) columns.get(12)).value();
        if (rssi < -100 || rssi > -60 || interval < 0 || interval > 10 || authInterval < 0 || authInterval > 10) {
            throw new IllegalArgumentException("Receive forwarding value out of range");
        }
    }

    private int integer(int column) { return ((SnmpValue.IntegerValue) columns.get(column)).value(); }
}
