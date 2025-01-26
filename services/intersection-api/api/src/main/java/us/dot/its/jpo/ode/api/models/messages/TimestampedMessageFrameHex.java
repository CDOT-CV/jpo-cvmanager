package us.dot.its.jpo.ode.api.models.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Timestamped Message Frame that serializes the asn1 data as hex instead of the default base 64
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class TimestampedMessageFrameHex extends TimestampedMessageFrame {

    public TimestampedMessageFrameHex() { super(); }
    public TimestampedMessageFrameHex(TimestampedMessageFrame messageFrame) {
        this.timestamp = messageFrame.timestamp;
        this.messageFrameType = messageFrame.messageFrameType;
        this.bytes = messageFrame.bytes;
    }

    @Override
    @JsonIgnore
    public byte[] getMessageFrame() {
        return super.getMessageFrame();
    }

    @JsonProperty("hex")
    public String getHex() {
        return getMessageFrameHex();
    }
}
