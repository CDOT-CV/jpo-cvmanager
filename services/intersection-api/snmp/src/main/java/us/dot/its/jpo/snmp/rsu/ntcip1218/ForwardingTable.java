package us.dot.its.jpo.snmp.rsu.ntcip1218;

import us.dot.its.jpo.snmp.client.SnmpOid;

/**
 * OID layouts for the received- and transmitted-message forwarding tables.
 * Column accessors return MIB subidentifiers, not zero-based array positions. Definitions follow
 * NTCIP1218-v01.txt assignments rather than its occasionally inconsistent prose.
 */
public enum ForwardingTable {
    /** Messages received over V2X and forwarded to a network destination. */
    RECEIVE("1.3.6.1.4.1.1206.4.2.18.5.2", 12, 10, 11, 8, 9),
    /** Messages transmitted over V2X and copied to a network destination. */
    TRANSMIT("1.3.6.1.4.1.1206.4.2.18.20.2", 9, 9, 8, 6, 7);
    private final SnmpOid root;
    private final int lastColumn, status, security, start, stop;
    ForwardingTable(String root, int lastColumn, int status, int security, int start, int stop) {
        this.root = new SnmpOid(root); this.lastColumn = lastColumn; this.status = status;
        this.security = security; this.start = start; this.stop = stop;
    }

    /** Returns the subtree root used for a complete table walk. */
    public SnmpOid root() { return root; }
    public int lastColumn() { return lastColumn; }
    public int statusColumn() { return status; }
    public int securityColumn() { return security; }
    public int startColumn() { return start; }
    public int stopColumn() { return stop; }

    /**
     * Builds one accessible column/row OID without performing I/O.
     * @param column column number, from 2 through lastColumn(); index column 1 is not accessible
     * @param index MIB row index, 1-255; create procedures apply their separate compatibility limit
     * @return numeric OID for the cell
     * @throws IllegalArgumentException if either number is outside the table layout
     */
    public SnmpOid cell(int column, int index) {
        if (column < 2 || column > lastColumn || index < 1 || index > 255) throw new IllegalArgumentException("Invalid forwarding cell");
        return root.append(1).append(column).append(index);
    }
}
