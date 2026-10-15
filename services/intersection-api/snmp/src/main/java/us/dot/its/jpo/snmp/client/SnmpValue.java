package us.dot.its.jpo.snmp.client;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;

/** Bytes use immutable hex storage; no mutable arrays escape into a request. */
public sealed interface SnmpValue {
    record IntegerValue(int value) implements SnmpValue {}
    record UnsignedValue(Kind kind, BigInteger value) implements SnmpValue {
        public enum Kind { COUNTER32, GAUGE32, TIMETICKS, COUNTER64 }
        public UnsignedValue {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(value);
            int bits = kind == Kind.COUNTER64 ? 64 : 32;
            if (value.signum() < 0 || value.bitLength() > bits) throw new IllegalArgumentException("Unsigned value out of range");
        }
    }
    record Octets(String hex) implements SnmpValue {
        public Octets {
            Objects.requireNonNull(hex);
            HexFormat.of().parseHex(hex);
            hex = hex.toUpperCase(java.util.Locale.ROOT);
        }
        public static Octets bytes(byte[] bytes) { return new Octets(HexFormat.of().formatHex(bytes)); }
        public static Octets text(String text) { return bytes(text.getBytes(StandardCharsets.UTF_8)); }
        public byte[] bytes() { return HexFormat.of().parseHex(hex); }
        public String text() { return new String(bytes(), StandardCharsets.UTF_8); }
    }
    record OidValue(SnmpOid value) implements SnmpValue {
        public OidValue { Objects.requireNonNull(value); }
    }
    record IpAddress(String value) implements SnmpValue {
        public IpAddress { SnmpTarget.validateIpv4(value); }
    }
    record NullValue() implements SnmpValue {}
    record ExceptionValue(Kind kind) implements SnmpValue {
        public enum Kind { NO_SUCH_OBJECT, NO_SUCH_INSTANCE, END_OF_MIB_VIEW }
        public ExceptionValue { Objects.requireNonNull(kind); }
    }
}
