package us.dot.its.jpo.snmp.snmp4j;

import java.math.BigInteger;
import org.snmp4j.smi.*;
import us.dot.its.jpo.snmp.client.SnmpException;
import us.dot.its.jpo.snmp.client.SnmpOid;
import us.dot.its.jpo.snmp.client.SnmpValue;

/** Only this package knows SNMP4J wire types. */
final class Snmp4jCodec {
    private Snmp4jCodec() {}

    static Variable encode(SnmpValue value) {
        return switch (value) {
            case SnmpValue.IntegerValue v -> new Integer32(v.value());
            case SnmpValue.Octets v -> new OctetString(v.bytes());
            case SnmpValue.OidValue v -> new OID(v.value().value());
            case SnmpValue.IpAddress v -> new IpAddress(v.value());
            case SnmpValue.UnsignedValue v -> switch (v.kind()) {
                case GAUGE32 -> new Gauge32(v.value().longValueExact());
                case TIMETICKS -> new TimeTicks(v.value().longValueExact());
                default -> throw new IllegalArgumentException("Counter values are read-only");
            };
            default -> throw new IllegalArgumentException("Value cannot be SET");
        };
    }

    static SnmpValue decode(Variable value) {
        if (value == null) throw new SnmpException(SnmpException.Kind.INVALID_OUTPUT, false);
        try {
            return switch (value.getSyntax()) {
                case SMIConstants.SYNTAX_INTEGER -> new SnmpValue.IntegerValue(((Integer32) value).getValue());
                case SMIConstants.SYNTAX_OCTET_STRING -> SnmpValue.Octets.bytes(((OctetString) value).getValue());
                case SMIConstants.SYNTAX_OBJECT_IDENTIFIER -> new SnmpValue.OidValue(new SnmpOid(value.toString()));
                case SMIConstants.SYNTAX_IPADDRESS -> new SnmpValue.IpAddress(value.toString());
                case SMIConstants.SYNTAX_COUNTER32 -> unsigned(SnmpValue.UnsignedValue.Kind.COUNTER32, value.toString());
                case SMIConstants.SYNTAX_GAUGE32 -> unsigned(SnmpValue.UnsignedValue.Kind.GAUGE32, value.toString());
                case SMIConstants.SYNTAX_TIMETICKS -> new SnmpValue.UnsignedValue(SnmpValue.UnsignedValue.Kind.TIMETICKS,
                        BigInteger.valueOf(((TimeTicks) value).getValue()));
                case SMIConstants.SYNTAX_COUNTER64 -> unsigned(SnmpValue.UnsignedValue.Kind.COUNTER64, value.toString());
                case SMIConstants.SYNTAX_NULL -> new SnmpValue.NullValue();
                case SMIConstants.EXCEPTION_NO_SUCH_OBJECT -> new SnmpValue.ExceptionValue(SnmpValue.ExceptionValue.Kind.NO_SUCH_OBJECT);
                case SMIConstants.EXCEPTION_NO_SUCH_INSTANCE -> new SnmpValue.ExceptionValue(SnmpValue.ExceptionValue.Kind.NO_SUCH_INSTANCE);
                case SMIConstants.EXCEPTION_END_OF_MIB_VIEW -> new SnmpValue.ExceptionValue(SnmpValue.ExceptionValue.Kind.END_OF_MIB_VIEW);
                default -> throw new SnmpException(SnmpException.Kind.INVALID_OUTPUT, false);
            };
        } catch (IllegalArgumentException | ClassCastException failure) {
            throw new SnmpException(SnmpException.Kind.INVALID_OUTPUT, false);
        }
    }

    private static SnmpValue unsigned(SnmpValue.UnsignedValue.Kind kind, String value) {
        return new SnmpValue.UnsignedValue(kind, new BigInteger(value));
    }
}
