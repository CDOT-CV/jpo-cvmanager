package us.dot.its.jpo.snmp.rsu.ntcip1218;

import us.dot.its.jpo.snmp.client.SnmpValue;

public enum MessageType {
    BSM("20", 46800, false), SPAT("8002", 44910, true), MAP("E0000017", 44920, true),
    SSM("E0000015", 44900, true), SRM("E0000016", 44930, false), TIM("8003", 47900, true);
    private final SnmpValue.Octets psid;
    private final int port;
    private final boolean transmit;
    MessageType(String psid, int port, boolean transmit) {
        this.psid = new SnmpValue.Octets(psid); this.port = port; this.transmit = transmit;
    }
    public SnmpValue.Octets psid() { return psid; }
    public int port() { return port; }
    public ForwardingTable table() { return transmit ? ForwardingTable.TRANSMIT : ForwardingTable.RECEIVE; }
}
