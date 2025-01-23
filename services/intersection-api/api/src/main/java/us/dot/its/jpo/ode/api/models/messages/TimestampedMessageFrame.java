package us.dot.its.jpo.ode.api.models.messages;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import static java.lang.Byte.toUnsignedInt;

/**
 * Timestamped byte data containing a MessageFrame, decoded from a PCAP packet
 */
@Data
@Slf4j
public class TimestampedMessageFrame {

    /**
     * Timestamp of the data frame, epoch milliseconds
     */
    @JsonProperty("timestamp")
    long timestamp;

    @JsonProperty("type")
    MessageType messageFrameType;

    @ToString.Exclude
    @JsonIgnore
    byte[] bytes;


    private final static HexFormat hexFormat = HexFormat.of();

    @JsonProperty("base64")
    public byte[] getMessageFrame() {
        return bytes;
    }

    //@JsonProperty("asn1Message")
    @JsonIgnore
    public String getMessageFrameHex() {
        return hexFormat.formatHex(bytes);
    }

    @JsonProperty("base64")
    public void setMessageFrame(byte[] bytes) {
        this.bytes = bytes;
        if (!findMessageFrame()) {
            log.warn("No MessageFrame was found in raw data: {}", getMessageFrameHex());
        }
    }

    /**
     * Search for a J2735 Message Frame, and set startIndex and endIndex to the beginning and end of it
     * @return true if a MessageFrame was detected, false if not
     */
    public boolean findMessageFrame() {
        if (bytes == null) {
            return false;
        }

        if (bytes.length < 7) {
            log.warn("Less than 7 bytes in message");
            return false;
        }

        final byte[] slice = new byte[7];
        // Scan for OER length determinant and message frame id
        for (int idx = 0; idx < bytes.length - 7; idx++) {
            System.arraycopy(bytes, idx, slice, 0, 7);
            if (checkIfMessageFrame(idx, slice)) {
                return true;
            }
        }

        // Couldn't find wrapper, check if it may be an uwrapped msg frame:
        // is there is a message frame id at the beginning?
        // Do this after the OER scan to avoid false positives with the first 2 bytes.
        if (checkIfMessageFrameAtBeginning()) {
            return true;
        }

        messageFrameType = MessageType.UNKNOWN;
        return false;
    }

    /**
     * Check a 7 item byte array for the pattern:
     * <p>OER unsecured data tag, followed by OER length determinant, Message Frame ID</p>
     * <p>Side effect: sets message frame type beginIndex and endIndex if found</p>
     * @param slice A 7 item byte array
     * @return True if found
     */
    public boolean checkIfMessageFrame(final int sliceStartIndex, final byte[] slice) {
        final int[] b = new int[7];
        for (int i = 0; i < 7; i++) {
            b[i] = toUnsignedInt(slice[i]);
        }

        // Check for OER unsecured data tag
        if (!(b[0] == 0x03 && b[1] == 0x80)) {
            // OER unsecured tag not there
            return false;
        }

        // Check for OER length determinant
        // First byte can be length less than 128, or marker that the next 2 bytes are the length
        if (b[2] < 0x80) {
            // It could be a length, check for message frame
            final var type = MessageType.fromId(b[4]);
            if (b[3] == 0 && type != null) {
                return validateIndices(sliceStartIndex + 3, b[2], type);
            }
            return false;
        }

        // Check for one byte length determinant
        if (b[2] == 0x81) {
            // b[3] Could be a length if it is >= 128
            if (!(b[3] >= 0x80)) {
                return false; // Nope
            }
            final var type = MessageType.fromId(b[5]);
            if (b[4] == 0 && type != null) {
                return validateIndices(sliceStartIndex + 4, b[3], type);
            }
            return false;
        }

        // Check for two byte length determinant
        if (b[2] == 0x82) {
            // b[3] + b[4] could be a 16 bit length
            final var type = MessageType.fromId(b[6]);
            if (b[5] == 0 && type != null) {
                // Combine b3 and b4 into a 16 bit integer
                int length = (b[3] << 8) | b[4];
                return validateIndices(sliceStartIndex + 5, length, type);
            }
            return false;
        }

        // Don't check for any larger length determinants.
        // In the unlikely event there is a Message Frame longer than 65535, this won't work

        return false;
    }

    public boolean checkIfMessageFrameAtBeginning() {
        int first = toUnsignedInt(bytes[0]);
        int second = toUnsignedInt(bytes[1]);
        MessageType type = MessageType.fromId(second);
        if (first == 0 && type != null) {
            messageFrameType = type;
            return true;
        }
        return false;
    }

    /**
     * Validate indices don't overflow reset the byte array to the extracted the message using the indices.
     * @param iStart Start index
     * @param length End index
     * @return true if valid, false if overflow
     */
    private boolean validateIndices(final int iStart, final int length, final MessageType messageId) {
        int iEnd = iStart + length;
        if (iEnd <= bytes.length) {
            // Resize the byte array
            bytes = Arrays.copyOfRange(bytes, iStart, iEnd);
            this.messageFrameType = messageId;
            return true;
        }
        log.warn("Tried to set invalid end index {}, based on length determinant: {}, " +
                "greater than {}, the number of bytes in the raw data.  " +
                "The data may be truncated: {}", iEnd, length, bytes.length, getMessageFrameHex());
        return false;
    }

}
