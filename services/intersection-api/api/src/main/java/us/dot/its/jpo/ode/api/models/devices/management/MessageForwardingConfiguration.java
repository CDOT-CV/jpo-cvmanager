package us.dot.its.jpo.ode.api.models.devices.management;

import java.time.OffsetDateTime;

/** One forwarding row with UTC timestamps, independent of its database or live SNMP source. */
public record MessageForwardingConfiguration(Table table, int index, String messageType,
        String destinationIp, int destinationPort, OffsetDateTime startDatetime, OffsetDateTime endDatetime,
        boolean active, boolean includeSecurity) {
    /** Cached RSU 4.1 rows remain viewable; live operations support NTCIP 1218 only. */
    public enum Table { RECEIVE, TRANSMIT, DSRC }
}
