package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.nio.ByteBuffer;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import us.dot.its.jpo.snmp.client.SnmpValue;

/** Converts the eight-octet UTC dates used by the forwarding MIBs. */
public final class NtcipDateAndTime {
    private NtcipDateAndTime() {}

    /**
     * Encodes UTC calendar fields, truncating fractions to tenths of a second.
     * The wire value has no timezone offset; callers must convert to UTC before calling.
     *
     * @param time calendar fields already converted to UTC
     * @return immutable eight-byte DateAndTime value
     * @throws IllegalArgumentException if the year is outside 1-65535
     */
    public static SnmpValue.Octets encode(LocalDateTime time) {
        if (time.getYear() < 1 || time.getYear() > 65535) throw new IllegalArgumentException("Date year out of range");

        // Layout: two-byte year, then month, day, hour, minute, second and decisecond.
        return SnmpValue.Octets.bytes(ByteBuffer.allocate(8).putShort((short) time.getYear())
                .put((byte) time.getMonthValue()).put((byte) time.getDayOfMonth()).put((byte) time.getHour())
                .put((byte) time.getMinute()).put((byte) time.getSecond()).put((byte) (time.getNano() / 100_000_000)).array());
    }

    /**
     * Decodes UTC calendar fields without applying the machine's timezone.
     *
     * @param value eight-byte date without an offset field
     * @return decoded UTC calendar fields
     * @throws IllegalArgumentException if the value is not eight octets or has invalid date/time fields
     */
    public static LocalDateTime decode(SnmpValue.Octets value) {
        byte[] bytes = value.bytes();
        if (bytes.length != 8 || Byte.toUnsignedInt(bytes[7]) > 9) throw new IllegalArgumentException("Invalid DateAndTime length/decisecond");

        try {
            return LocalDateTime.of(Short.toUnsignedInt(ByteBuffer.wrap(bytes).getShort()), Byte.toUnsignedInt(bytes[2]),
                    Byte.toUnsignedInt(bytes[3]), Byte.toUnsignedInt(bytes[4]), Byte.toUnsignedInt(bytes[5]),
                    Byte.toUnsignedInt(bytes[6]), Byte.toUnsignedInt(bytes[7]) * 100_000_000);
        } catch (DateTimeException failure) {
            // Row readers translate validation failures to INVALID_OUTPUT and retain write uncertainty.
            throw new IllegalArgumentException("Invalid DateAndTime calendar fields");
        }
    }
}
