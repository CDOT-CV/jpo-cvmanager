package us.dot.its.jpo.snmp.client;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Typed SNMP values shared by adapters and applications. Missing values remain explicit markers.
 * Byte strings use immutable hex storage; arrays passed in or returned do not mutate stored values.
 */
public sealed interface SnmpValue {
    /**
     * A signed SNMP Integer32.
     * @param value signed 32-bit value
     */
    record IntegerValue(int value) implements SnmpValue {}

    /**
     * An unsigned counter, gauge or time-tick value.
     * @param kind wire type and width
     * @param value nonnegative value fitting the selected 32-bit or 64-bit type
     */
    record UnsignedValue(Kind kind, BigInteger value) implements SnmpValue {
        /** Unsigned SNMP wire types; only GAUGE32 and TIMETICKS are writable through the adapter. */
        public enum Kind { COUNTER32, GAUGE32, TIMETICKS, COUNTER64 }

        /**
         * Validates that the value fits the selected unsigned width.
         * @throws IllegalArgumentException if the value is negative or too large
         */
        public UnsignedValue {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(value);
            int bits = kind == Kind.COUNTER64 ? 64 : 32;
            if (value.signum() < 0 || value.bitLength() > bits) throw new IllegalArgumentException("Unsigned value out of range");
        }
    }

    /**
     * An immutable byte string stored as uppercase hexadecimal. Not all byte strings are text.
     * @param hex hexadecimal with no separators and an even number of digits
     */
    record Octets(String hex) implements SnmpValue {
        /**
         * Validates and normalizes the hexadecimal input.
         * @throws IllegalArgumentException if the hex text is malformed
         */
        public Octets {
            Objects.requireNonNull(hex);
            HexFormat.of().parseHex(hex);
            hex = hex.toUpperCase(java.util.Locale.ROOT);
        }

        /**
         * Copies raw bytes into immutable storage.
         * @param bytes source bytes
         * @return stored byte string
         */
        public static Octets bytes(byte[] bytes) { return new Octets(HexFormat.of().formatHex(bytes)); }

        /**
         * Encodes text as UTF-8 bytes.
         * @param text text to encode
         * @return UTF-8 byte string
         */
        public static Octets text(String text) { return bytes(text.getBytes(StandardCharsets.UTF_8)); }

        /**
         * Returns a fresh byte array on every call.
         * @return a copy of the stored bytes
         */
        public byte[] bytes() { return HexFormat.of().parseHex(hex); }

        /**
         * Decodes bytes as UTF-8, replacing malformed sequences. Use bytes() for binary values.
         * @return decoded text
         */
        public String text() { return new String(bytes(), StandardCharsets.UTF_8); }
    }

    /**
     * An object identifier carried as an SNMP value.
     * @param value numeric OID
     */
    record OidValue(SnmpOid value) implements SnmpValue {
        public OidValue { Objects.requireNonNull(value); }
    }

    /**
     * A four-octet IPv4 address value.
     * @param value literal dotted-decimal IPv4 address
     */
    record IpAddress(String value) implements SnmpValue {
        public IpAddress { SnmpTarget.validateIpv4(value); }
    }

    /** The SNMP NULL value; distinct from missing-object exception markers. */
    record NullValue() implements SnmpValue {}

    /**
     * A protocol-level absence or traversal marker, rather than an operation failure.
     * @param kind marker supplied by the agent
     */
    record ExceptionValue(Kind kind) implements SnmpValue {
        /** Missing object, missing instance, or end of a table walk. */
        public enum Kind { NO_SUCH_OBJECT, NO_SUCH_INSTANCE, END_OF_MIB_VIEW }
        public ExceptionValue { Objects.requireNonNull(kind); }
    }
}
