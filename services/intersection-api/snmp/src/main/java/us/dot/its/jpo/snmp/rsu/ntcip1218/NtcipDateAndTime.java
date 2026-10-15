package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import us.dot.its.jpo.snmp.client.SnmpValue;

public final class NtcipDateAndTime {
    private NtcipDateAndTime() {}
    /** Eight-byte local time without offset, as used by the existing forwarding implementation. */
    public static SnmpValue.Octets encode(LocalDateTime time) {
        if (time.getYear() < 1 || time.getYear() > 65535) throw new IllegalArgumentException("Date year out of range");
        return SnmpValue.Octets.bytes(ByteBuffer.allocate(8).putShort((short) time.getYear())
                .put((byte) time.getMonthValue()).put((byte) time.getDayOfMonth()).put((byte) time.getHour())
                .put((byte) time.getMinute()).put((byte) time.getSecond()).put((byte) (time.getNano() / 100_000_000)).array());
    }
    public static LocalDateTime decode(SnmpValue.Octets value) {
        byte[] bytes = value.bytes();
        if (bytes.length != 8 || Byte.toUnsignedInt(bytes[7]) > 9) throw new IllegalArgumentException("Invalid DateAndTime length/decisecond");
        return LocalDateTime.of(Short.toUnsignedInt(ByteBuffer.wrap(bytes).getShort()), Byte.toUnsignedInt(bytes[2]),
                Byte.toUnsignedInt(bytes[3]), Byte.toUnsignedInt(bytes[4]), Byte.toUnsignedInt(bytes[5]),
                Byte.toUnsignedInt(bytes[6]), Byte.toUnsignedInt(bytes[7]) * 100_000_000);
    }
}
