package us.dot.its.jpo.ode.api.models.messages;

import lombok.Data;

@Data
public class TimestampedMessageFrameXml {

    /**
     * Timestamp of the data frame, epoch milliseconds
     */
    private long timestamp;
    private MessageType type;
    private String xml;

}
