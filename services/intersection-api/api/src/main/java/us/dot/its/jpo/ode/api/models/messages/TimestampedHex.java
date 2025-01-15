package us.dot.its.jpo.ode.api.models.messages;

import lombok.Data;
import lombok.Generated;

/**
 * A Timestamped Hex message decoded from a PCAP packet
 */
@Data
@Generated
public class TimestampedHex {
    long timestamp;
    String hexMessage;
}
