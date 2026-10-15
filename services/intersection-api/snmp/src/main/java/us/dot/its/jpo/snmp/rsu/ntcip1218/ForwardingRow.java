package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import us.dot.its.jpo.snmp.client.SnmpValue;

/** Retains unknown PSIDs and all columns rather than dropping rows the application does not recognize. */
public record ForwardingRow(ForwardingTable table, int index, Map<Integer, SnmpValue> columns) {
    public ForwardingRow {
        Objects.requireNonNull(table);
        columns = Map.copyOf(columns);
        table.cell(2, index);
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
        NtcipDateAndTime.decode((SnmpValue.Octets) columns.get(table.startColumn()));
        NtcipDateAndTime.decode((SnmpValue.Octets) columns.get(table.stopColumn()));
    }
    public SnmpValue.Octets psid() { return (SnmpValue.Octets) columns.get(2); }
    public String destination() { return ((SnmpValue.Octets) columns.get(3)).text(); }
    public int port() { return integer(4); }
    public int status() { return integer(table.statusColumn()); }
    public boolean includeSecurity() { return integer(table.securityColumn()) == 1; }
    public LocalDateTime start() { return NtcipDateAndTime.decode((SnmpValue.Octets) columns.get(table.startColumn())); }
    public LocalDateTime stop() { return NtcipDateAndTime.decode((SnmpValue.Octets) columns.get(table.stopColumn())); }
    private int integer(int column) { return ((SnmpValue.IntegerValue) columns.get(column)).value(); }
}
