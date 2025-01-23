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
public class TimestampedHex {

    /**
     * Timestamp of the data frame
     */
    @JsonProperty("ts")
    long timestamp;

    @JsonProperty("mt")
    String messageType;

    @ToString.Exclude
    @JsonIgnore
    byte[] bytes;

    /**
     * Start index (inclusive) of the MessageFrame in the byte array
      */
    @JsonIgnore
    int startIndex;

    /**
     * End index (exclusive) of the MessageFrame in the byte array
      */
    @JsonIgnore
    int endIndex;

    /**
     * Path to the data within the frame.
     */
    @JsonIgnore
    String path;

    private final static HexFormat hexFormat = HexFormat.of();

    @JsonProperty("mf")
    public byte[] getMessageFrame() {
        return Arrays.copyOfRange(bytes, startIndex, endIndex);
    }

    @JsonIgnore
    public String getMessageFrameHex() {
        return hexFormat.formatHex(bytes, startIndex, endIndex);
    }

    @JsonIgnore
    public void setRawDataHex(String hex) {
        bytes = hexFormat.parseHex(hex);
        if (!findMessageFrame()) {
            log.warn("No MessageFrame was found in raw data: {}", hex);
        }
    }

    @JsonIgnore
    public String getRawDataHex() {
        if (bytes == null) return "";
        return hexFormat.formatHex(bytes);
    }

    @JsonIgnore
    public byte[] getRawData() {
        return bytes;
    }

    @JsonIgnore
    public void setRawData(byte[] bytes) {
        this.bytes = bytes;
        if (!findMessageFrame()) {
            //log.warn("No MessageFrame was found in raw data: {}", getRawDataHex());
        }
    }

    /**
     * Search for a J2735 Message Frame, and set startIndex and endIndex to the beginning and end of it
     * @return true if a MessageFrame was detected, false if not
     */
    public boolean findMessageFrame() {
        startIndex = 0;
        if (bytes != null) {
            endIndex = bytes.length;
        } else {
            endIndex = 0;
            return false;
        }
        if (path == null) {
            throw new RuntimeException("Path is not set. Path must be set before setting data.");
        }
        if (path.contains("unsecuredData_raw")) {
            // We already have the unwrapped message frame
            return true;
        }

        final byte[] slice = new byte[7];
        // Scan for patterns
        for (int idx = 0; idx < bytes.length - 7; idx++) {
            System.arraycopy(bytes, idx, slice, 0, 7);
            if (checkIfMessageFrame(idx, slice)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check a 7 item byte array for the pattern:
     * <p>OER unsecured data tag, followed by OER length determinant, Message Frame ID</p>
     * <p>Side effect: sets beginIndex and endIndex if found</p>
     * @param slice A 7 item byte array
     * @return True if found
     */
    public boolean checkIfMessageFrame(int sliceStartIndex, byte[] slice) {
        int[] b = new int[7];
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
            if (b[3] == 0 && MESSAGE_FRAME_IDS.contains(b[4])) {
                return validateIndices(sliceStartIndex + 3, b[2]);
            }
            return false;
        }

        // Check for one byte length determinant
        if (b[2] == 0x81) {
            // b[3] Could be a length if it is >= 128
            if (!(b[3] >= 0x80)) {
                return false; // Nope
            }
            if (b[4] == 0 && MESSAGE_FRAME_IDS.contains(b[5])) {
                return validateIndices(sliceStartIndex + 4, b[3]);
            }
            return false;
        }

        // Check for two byte length determinant
        if (b[2] == 0x82) {
            // b[3] + b[4] could be a 16 bit length
            if (b[5] == 0 && MESSAGE_FRAME_IDS.contains(b[6])) {
                // Combine b3 and b4 into a 16 bit integer
                int length = (b[3] << 8) | b[4];
                return validateIndices(sliceStartIndex + 5, length);
            }
            return false;
        }

        // Don't check for any larger length determinants.
        // In the unlikely event there is a Message Frame longer than 65535, this won't work

        return false;
    }


    // Incomplete list of Message Frame IDs, just the ones of interest for intersections for now
    public static final Set<Integer> MESSAGE_FRAME_IDS = Set.of(
            0x12,   // MAP
            0x13,   // SPAT
            0x14,   // BSM
            0x1D,   // SRM
            0x1E    // SSM
    );

    /**
     * Validate indices don't overflow and set the message frame indices
     * @param iStart
     * @param length
     * @return true if valid, false if overflow
     */
    private boolean validateIndices(int iStart, int length) {
        int iEnd = iStart + length;
        if (iEnd <= bytes.length) {
            startIndex = iStart;
            endIndex = iEnd;
            return true;
        }
        log.warn("Tried to set invalid end index {}, based on length determinant: {}, " +
                "greater than {}, the number of bytes in the raw data.  " +
                "The data may be truncated: {}", iEnd, length, bytes.length, getRawDataHex());
        return false;
    }

}
