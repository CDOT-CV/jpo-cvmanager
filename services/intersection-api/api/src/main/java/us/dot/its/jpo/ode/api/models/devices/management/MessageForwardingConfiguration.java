package us.dot.its.jpo.ode.api.models.devices.management;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/** A cached or live forwarding row. Invalid live rows carry a warning and nullable fields. */
public record MessageForwardingConfiguration(Table table, int index, String messageType,
        String destinationIp, Integer destinationPort, OffsetDateTime startDatetime, OffsetDateTime endDatetime,
        Boolean active, Boolean includeSecurity,
        @JsonInclude(JsonInclude.Include.NON_NULL) String validationError) {
    /** Complete, validated rows keep the existing response shape. */
    public MessageForwardingConfiguration(Table table, int index, String messageType,
            String destinationIp, int destinationPort, OffsetDateTime startDatetime, OffsetDateTime endDatetime,
            boolean active, boolean includeSecurity) {
        this(table, index, messageType, destinationIp, destinationPort, startDatetime, endDatetime,
                active, includeSecurity, null);
    }

    /** Cached RSU 4.1 rows remain viewable; live operations support NTCIP 1218 only. */
    public enum Table { RECEIVE, TRANSMIT, DSRC }
}
