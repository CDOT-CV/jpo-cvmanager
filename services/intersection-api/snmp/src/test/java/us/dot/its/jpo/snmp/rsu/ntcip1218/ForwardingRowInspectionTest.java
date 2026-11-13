package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import us.dot.its.jpo.snmp.client.*;
import static org.junit.jupiter.api.Assertions.*;

class ForwardingRowInspectionTest {
    private static final Ntcip1218Device DEVICE = new Ntcip1218Device("192.0.2.10",
            new SnmpCredentials("fixture-user", "fixture-auth-pass", "fixture-privacy-pass"));

    @ParameterizedTest
    @EnumSource(ForwardingTable.class)
    void publicInspectionFindsMalformedRowsAndSuppliesTheirRecoverySelection(ForwardingTable table) {
        var agent = new Ntcip1218ClientTest.MemoryAgent();
        var client = new Ntcip1218Client(agent, Clock.systemUTC());
        var message = table == ForwardingTable.RECEIVE ? MessageType.BSM : MessageType.MAP;
        client.addForwarding(DEVICE, new ForwardingRequest(message, "198.51.100.3", false));
        client.addForwarding(DEVICE, new ForwardingRequest(message, "198.51.100.4", false));
        agent.data.remove(table.cell(table.startColumn(), 2));
        agent.data.put(table.cell(3, 2), SnmpValue.Octets.text("2001:db8::1"));
        agent.data.put(table.cell(table.statusColumn(), 2), new SnmpValue.IntegerValue(3));
        assertThrows(SnmpException.class, () -> client.readForwarding(DEVICE));

        var rows = client.inspectForwarding(DEVICE);
        assertEquals(2, rows.size());
        assertTrue(rows.getFirst().validationError().isEmpty());
        var malformed = rows.stream().filter(row -> row.validationError().isPresent()).findFirst().orElseThrow();
        assertEquals(2, malformed.index());
        assertEquals(table, malformed.table());
        assertEquals("2001:db8::1", malformed.destination().orElseThrow());
        assertEquals(message.port(), malformed.port().orElseThrow());
        assertThrows(UnsupportedOperationException.class, rows::clear);
        assertThrows(UnsupportedOperationException.class, () -> malformed.columns().clear());

        // Discover the selection through the public diagnostic API instead of assuming seeded metadata.
        assertTrue(client.deleteForwarding(DEVICE, malformed.table(), malformed.index(),
                malformed.destination().orElseThrow(), malformed.port().orElseThrow()).changed());
        assertEquals(1, client.readForwarding(DEVICE).size());
        assertEquals(3, agent.writes.size());
    }

    @Test void unreadableDestinationOrPortIsExplicitInDiagnosticRows() {
        var agent = new Ntcip1218ClientTest.MemoryAgent();
        var table = ForwardingTable.RECEIVE;
        agent.data.put(table.cell(3, 1), new SnmpValue.IntegerValue(123));
        agent.data.put(table.cell(4, 1), SnmpValue.Octets.text("46800"));
        var row = new Ntcip1218Client(agent, Clock.systemUTC()).inspectForwarding(DEVICE).getFirst();
        assertTrue(row.destination().isEmpty());
        assertTrue(row.port().isEmpty());
        assertTrue(row.validationError().isPresent());
    }

    @Test void inspectionStillRejectsMalformedOidStructureAndFailedWalks() {
        var agent = new Ntcip1218ClientTest.MemoryAgent();
        agent.data.put(ForwardingTable.RECEIVE.root().append(1).append(99).append(1), new SnmpValue.IntegerValue(1));
        var client = new Ntcip1218Client(agent, Clock.systemUTC());
        assertEquals(SnmpException.Kind.INVALID_OUTPUT,
                assertThrows(SnmpException.class, () -> client.inspectForwarding(DEVICE)).kind());
        var failing = new Ntcip1218ClientTest.MemoryAgent() {
            @Override public List<SnmpBinding> walk(SnmpTarget target, SnmpOid root) {
                if (root.equals(ForwardingTable.TRANSMIT.root())) throw new SnmpException(SnmpException.Kind.TIMEOUT, false);
                return super.walk(target, root);
            }
        };
        failing.data.put(ForwardingTable.RECEIVE.cell(4, 1), new SnmpValue.IntegerValue(46800));
        assertEquals(SnmpException.Kind.TIMEOUT, assertThrows(SnmpException.class,
                () -> new Ntcip1218Client(failing, Clock.systemUTC()).inspectForwarding(DEVICE)).kind());
    }
}
