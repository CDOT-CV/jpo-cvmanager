package us.dot.its.jpo.snmp.rsu.ntcip1218;

import us.dot.its.jpo.snmp.client.SnmpOid;

/** Numeric definitions from NTCIP1218-v01.txt assignments, not its occasionally inconsistent prose. */
public enum ForwardingTable {
    RECEIVE("1.3.6.1.4.1.1206.4.2.18.5.2", 12, 10, 11, 8, 9),
    TRANSMIT("1.3.6.1.4.1.1206.4.2.18.20.2", 9, 9, 8, 6, 7);
    private final SnmpOid root;
    private final int lastColumn, status, security, start, stop;
    ForwardingTable(String root, int lastColumn, int status, int security, int start, int stop) {
        this.root = new SnmpOid(root); this.lastColumn = lastColumn; this.status = status;
        this.security = security; this.start = start; this.stop = stop;
    }
    public SnmpOid root() { return root; }
    public int lastColumn() { return lastColumn; }
    public int statusColumn() { return status; }
    public int securityColumn() { return security; }
    public int startColumn() { return start; }
    public int stopColumn() { return stop; }
    public SnmpOid cell(int column, int index) {
        if (column < 2 || column > lastColumn || index < 1 || index > 255) throw new IllegalArgumentException("Invalid forwarding cell");
        return root.append(1).append(column).append(index);
    }
}
