package us.dot.its.jpo.snmp.snmp4j;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.snmp4j.smi.Counter64;
import org.snmp4j.smi.Null;
import us.dot.its.jpo.snmp.client.*;
import static org.junit.jupiter.api.Assertions.*;

class Snmp4jCodecTest {
    @Test void longNumericOidsFailValidationWithoutOverflowingTheStack() {
        assertThrows(IllegalArgumentException.class, () -> new SnmpOid("1" + ".1".repeat(10000)));
        assertThrows(IllegalArgumentException.class, () -> new SnmpOid("1" + ".1".repeat(10000) + ".bad"));
        assertEquals("1.3.6.1", new SnmpOid(".1.03.006.1").value());
    }

    @Test void roundTripsTypedWritableValues() {
        for (SnmpValue value : List.of(new SnmpValue.IntegerValue(-1), new SnmpValue.Octets("00FF0A"),
                new SnmpValue.OidValue(new SnmpOid("1.3.6.1")), new SnmpValue.IpAddress("192.0.2.1"),
                new SnmpValue.UnsignedValue(SnmpValue.UnsignedValue.Kind.GAUGE32, new BigInteger("4294967295")),
                new SnmpValue.UnsignedValue(SnmpValue.UnsignedValue.Kind.TIMETICKS, new BigInteger("4294967295")))) {
            assertEquals(value, Snmp4jCodec.decode(Snmp4jCodec.encode(value)));
        }
    }
    @Test void counter64IsUnsignedAndExceptionSyntaxNeverBecomesAnInteger() {
        assertEquals(new SnmpValue.UnsignedValue(SnmpValue.UnsignedValue.Kind.COUNTER64, new BigInteger("18446744073709551615")),
                Snmp4jCodec.decode(new Counter64(-1L)));
        assertInstanceOf(SnmpValue.ExceptionValue.class, Snmp4jCodec.decode(Null.noSuchObject));
        assertInstanceOf(SnmpValue.ExceptionValue.class, Snmp4jCodec.decode(Null.noSuchInstance));
        assertThrows(IllegalArgumentException.class, () -> Snmp4jCodec.encode(new SnmpValue.ExceptionValue(SnmpValue.ExceptionValue.Kind.NO_SUCH_OBJECT)));
    }
}
