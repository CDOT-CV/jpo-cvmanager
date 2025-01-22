package us.dot.its.jpo.ode.api.models.messages;

import java.util.HexFormat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;


/**
 * A Timestamped Hex message decoded from a PCAP packet
 */
@Data
public class TimestampedHex {

    /**
     * Timestamp of the data frame
     */
    long timestamp;

    @ToString.Exclude
    @JsonIgnore
    byte[] bytes;

    /**
     * Path to the data within the frame.
     */
    String path;

    private final static HexFormat hexFormat = HexFormat.of();

    public String getHexMessage() {
        if (bytes == null) return "";
        return hexFormat.formatHex(bytes);
    }

    public void setHexMessage(String hexMessage) {
        bytes = hexFormat.parseHex(hexMessage);
    }

}
