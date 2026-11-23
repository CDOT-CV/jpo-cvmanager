package us.dot.its.jpo.ode.api.services;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import us.dot.its.jpo.ode.api.models.postgres.tables.*;
import us.dot.its.jpo.ode.api.repositories.*;
import us.dot.its.jpo.snmp.client.SnmpBinding;
import us.dot.its.jpo.snmp.client.SnmpValue;
import us.dot.its.jpo.snmp.rsu.ntcip1218.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RsuMessageForwardingSnapshotServiceTest {
    private final RsuRepository rsus = mock(RsuRepository.class);
    private final SnmpMsgfwdConfigRepository configs = mock(SnmpMsgfwdConfigRepository.class);
    private final SnmpMsgfwdTypeRepository types = mock(SnmpMsgfwdTypeRepository.class);
    private final RsuMessageForwardingSnapshotService service = new RsuMessageForwardingSnapshotService(rsus, configs, types);

    private void types() {
        var receive = new SnmpMsgfwdType(); receive.setId(2);
        var transmit = new SnmpMsgfwdType(); transmit.setId(3);
        when(types.findByName("rsuReceivedMsg")).thenReturn(Optional.of(receive));
        when(types.findByName("rsuXmitMsgFwding")).thenReturn(Optional.of(transmit));
        when(rsus.getReferenceById(7)).thenReturn(new Rsu());
    }

    private ForwardingRow row(String destination) {
        var bindings = new Ntcip1218Client(mock(us.dot.its.jpo.snmp.client.SnmpClient.class), Clock.systemUTC())
                .createBindings(new ForwardingRequest(MessageType.MAP, destination, true), 1);
        var columns = bindings.stream().collect(Collectors.toMap(b -> {
            var parts = b.oid().value().split("\\."); return Integer.parseInt(parts[parts.length - 2]);
        }, SnmpBinding::value));
        columns.put(9, new SnmpValue.IntegerValue(1));
        return new ForwardingRow(ForwardingTable.TRANSMIT, 1, columns);
    }

    @Test void mapsVerifiedSnapshotAndScopesReplacementToOneRsu() throws Exception {
        types();
        var row = row("198.51.100.3");
        service.replace(7, "192.0.2.10", List.of(row));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SnmpMsgfwdConfig>> capture = ArgumentCaptor.forClass(List.class);
        var order = inOrder(configs);
        order.verify(configs).removeSnmpMsgfwdConfigByIpv4Address(java.net.InetAddress.getByName("192.0.2.10"));
        order.verify(configs).saveAllAndFlush(capture.capture());
        var config = capture.getValue().getFirst();
        assertEquals(7, config.getId().getRsuId());
        assertEquals(3, config.getId().getMsgfwdType());
        assertEquals("MAP", config.getMessageType());
        assertEquals(44920, config.getDestPort());
        assertEquals("198.51.100.3", config.getDestIpv4().getHostAddress());
        assertTrue(config.getActive()); assertTrue(config.getSecurity());
        assertEquals(row.start().toInstant(ZoneOffset.UTC), config.getStartDatetime());
        assertEquals(row.stop().toInstant(ZoneOffset.UTC), config.getEndDatetime());
    }

    @Test void emptySuccessfulSnapshotClearsRowsButMissingTypeDoesNot() throws Exception {
        types();
        service.replace(7, "192.0.2.10", List.of());
        verify(configs).removeSnmpMsgfwdConfigByIpv4Address(java.net.InetAddress.getByName("192.0.2.10"));
        verify(configs).saveAllAndFlush(List.of());
        reset(configs);
        when(types.findByName("rsuReceivedMsg")).thenReturn(Optional.empty());
        assertThrows(java.util.NoSuchElementException.class,
                () -> service.replace(7, "192.0.2.10", List.of()));
        verifyNoInteractions(configs);
    }

    @Test void invalidDestinationInSnapshotDoesNotDeleteExistingRows() {
        types();
        var row = row("198.51.100.3");
        var columns = new java.util.HashMap<>(row.columns());
        columns.put(3, SnmpValue.Octets.text("not-an-ip"));
        assertThrows(IllegalArgumentException.class, () -> service.replace(7, "192.0.2.10",
                List.of(new ForwardingRow(row.table(), row.index(), columns))));
        verifyNoInteractions(configs);
    }
}
