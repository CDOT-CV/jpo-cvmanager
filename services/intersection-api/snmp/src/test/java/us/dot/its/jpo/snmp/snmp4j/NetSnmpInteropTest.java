package us.dot.its.jpo.snmp.snmp4j;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import us.dot.its.jpo.snmp.client.*;
import us.dot.its.jpo.snmp.rsu.ntcip1218.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent implementation: SNMP4J client against disposable native Net-SNMP snmpd. */
@EnabledIfEnvironmentVariable(named = "JPO_SNMP_INTEGRATION", matches = "true")
class NetSnmpInteropTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 16})
    void realSnmpV3GetNextWalkSetAndSeparatePasswords(int bulkRepetitions) {
        try (var client = new Snmp4jClient(new Snmp4jOptions(Duration.ofSeconds(1), 0, Duration.ofSeconds(10),
                Duration.ofSeconds(1), 4, 8, 100, 1000, 1024 * 1024, bulkRepetitions))) {
            var credentials = new SnmpCredentials("fixture-user", "fixture-auth-pass", "fixture-privacy-pass");
            var target = new SnmpTarget("127.0.0.1", 1161, credentials);
            var description = new SnmpOid("1.3.6.1.2.1.1.1.0");
            assertInstanceOf(SnmpValue.Octets.class, client.get(target, List.of(description)).getFirst().value());
            assertTrue(client.getNext(target, List.of(description)).getFirst().oid().compareTo(description) > 0);
            assertFalse(client.walk(target, new SnmpOid("1.3.6.1.2.1.1")).isEmpty());
            var contact = new SnmpOid("1.3.6.1.2.1.1.4.0");
            var expected = new SnmpBinding(contact, SnmpValue.Octets.text("fixture contact ; $() \\\""));
            assertEquals(List.of(expected), client.set(target, List.of(expected)));
            assertEquals(List.of(expected), client.get(target, List.of(contact)));
            var readOnly = new SnmpBinding(description, SnmpValue.Octets.text("cannot write"));
            assertEquals(SnmpException.Kind.AGENT_REJECTED, assertThrows(SnmpException.class, () -> client.set(target, List.of(readOnly))).kind());
            assertInstanceOf(SnmpValue.ExceptionValue.class, client.get(target, List.of(new SnmpOid("1.3.6.1.2.1.1.4.12345"))).getFirst().value());
            var bad = new SnmpTarget("127.0.0.1", 1161, new SnmpCredentials("fixture-user", "wrong-password", "wrong-privacy"));
            assertThrows(SnmpException.class, () -> client.get(bad, List.of(contact)));
            var ntcip = new Ntcip1218Client(client, Clock.systemUTC(), ZoneOffset.UTC);
            var device = new Ntcip1218Device(target);
            assertEquals(2, ntcip.readStatus(device));
            assertEquals(-105.0342786, ntcip.readLocation(device).longitude().orElseThrow());
            assertEquals("fixture-fw", ntcip.readFirmwareVersion(device));
        }
    }
}
