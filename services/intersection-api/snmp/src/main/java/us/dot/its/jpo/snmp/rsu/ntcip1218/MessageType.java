package us.dot.its.jpo.snmp.rsu.ntcip1218;

import us.dot.its.jpo.snmp.client.SnmpValue;

/**
 * Message presets pairing a PSID with this application's UDP port and forwarding table.
 * Ports are application conventions, not port assignments mandated by the NTCIP MIB.
 * BSM and SRM use RECEIVE; the other presets use TRANSMIT.
 * @see ForwardingRequest
 */
public enum MessageType {
    BSM("20", 46800, false), SPAT("8002", 44910, true), MAP("E0000017", 44920, true),
    SSM("E0000015", 44900, true), SRM("E0000016", 44930, false), TIM("8003", 47900, true);
    private final SnmpValue.Octets psid;
    private final int port;
    private final boolean transmit;
    MessageType(String psid, int port, boolean transmit) {
        this.psid = new SnmpValue.Octets(psid); this.port = port; this.transmit = transmit;
    }

    /** Returns the PSID bytes used to match or configure this message. */
    public SnmpValue.Octets psid() { return psid; }

    /** Returns the preset destination UDP port. */
    public int port() { return port; }

    /** Returns the receive or transmit forwarding table for this preset. */
    public ForwardingTable table() { return transmit ? ForwardingTable.TRANSMIT : ForwardingTable.RECEIVE; }
}
