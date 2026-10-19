package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import us.dot.its.jpo.ode.api.config.SnmpProperties;
import us.dot.its.jpo.ode.api.models.devices.management.MessageForwardingConfiguration.Table;
import us.dot.its.jpo.ode.api.models.postgres.tables.*;
import us.dot.its.jpo.ode.api.repositories.*;
import us.dot.its.jpo.snmp.client.SnmpCredentials;
import us.dot.its.jpo.snmp.execution.*;
import us.dot.its.jpo.snmp.rsu.ntcip1218.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RsuMessageForwardingReadServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T16:00:00Z"), ZoneOffset.UTC);
    private final RsuRepository rsus = mock(RsuRepository.class);
    private final SnmpMsgfwdConfigRepository configs = mock(SnmpMsgfwdConfigRepository.class);
    private final RsuMessageForwardingContextService contexts = mock(RsuMessageForwardingContextService.class);

    private RsuMessageForwardingReadService service(SnmpTaskExecutor executor) {
        return new RsuMessageForwardingReadService(rsus, configs, contexts, executor, new SnmpProperties(), CLOCK);
    }

    @Test void cachedReadNeedsNeitherCredentialsNorDeviceConnectivityAndPreservesLegacyRows() throws Exception {
        var executor = mock(SnmpTaskExecutor.class);
        var ip = InetAddress.getByName("192.0.2.10");
        when(rsus.findByIpv4Address(ip)).thenReturn(new Rsu());
        var type = new SnmpMsgfwdType(); type.setName("rsuDsrcFwd");
        var id = new SnmpMsgfwdConfigId(); id.setSnmpIndex(5);
        var row = new SnmpMsgfwdConfig(); row.setId(id); row.setMsgfwdType(type);
        row.setMessageType("map"); row.setDestIpv4(InetAddress.getByName("198.51.100.3")); row.setDestPort(44920);
        row.setStartDatetime(CLOCK.instant()); row.setEndDatetime(CLOCK.instant().plusSeconds(3600));
        row.setActive(true); row.setSecurity(false);
        when(configs.findForwardingByIpv4Address(ip)).thenReturn(List.of(row));
        var result = service(executor).cached("192.0.2.10").getFirst();
        assertEquals(Table.DSRC, result.table()); assertEquals(5, result.index());
        assertEquals("MAP", result.messageType()); assertEquals("198.51.100.3", result.destinationIp());
        assertEquals("2026-10-15T10:00-06:00", result.startDatetime().toString());
        assertTrue(result.active()); assertFalse(result.includeSecurity());
        verifyNoInteractions(contexts, executor);
    }

    @Test void emptyCacheIsSuccessfulButUnknownRsuAndDnsNamesAreErrors() throws Exception {
        var executor = mock(SnmpTaskExecutor.class);
        var ip = InetAddress.getByName("192.0.2.10");
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service(executor).cached("192.0.2.10")).getStatusCode().value());
        when(rsus.findByIpv4Address(ip)).thenReturn(new Rsu());
        when(configs.findForwardingByIpv4Address(ip)).thenReturn(List.of());
        assertEquals(List.of(), service(executor).cached("192.0.2.10"));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service(executor).cached("localhost")).getStatusCode().value());
        verifyNoInteractions(contexts, executor);
    }

    @Test void liveReadUsesTrackedExecutorAndNeverWritesDeviceOrDatabase() {
        var client = new RsuMessageForwardingServiceTest.MemoryClient();
        var device = device();
        new Ntcip1218Client(client, CLOCK).addForwarding(device,
                new ForwardingRequest(MessageType.MAP, "198.51.100.3", true));
        client.writes.set(0); client.walks.set(0);
        when(contexts.load(List.of("192.0.2.10"))).thenReturn(List.of(new RsuMessageForwardingContextService.DeviceContext(7, device)));
        try (var executor = new SnmpTaskExecutor(client, new SnmpExecutorOptions(1, 10, Duration.ofSeconds(1)))) {
            var properties = new SnmpProperties();
            var service = new RsuMessageForwardingReadService(rsus, configs, contexts, executor, properties, CLOCK);
            var result = service.live("192.0.2.10").getFirst();
            assertEquals(Table.TRANSMIT, result.table()); assertEquals("MAP", result.messageType());
            assertEquals(44920, result.destinationPort()); assertTrue(result.includeSecurity());
            assertEquals(CLOCK.instant(), result.startDatetime().toInstant());
            assertEquals("2026-10-15T10:00-06:00", result.startDatetime().toString());
            assertEquals("2036-10-15T10:00-06:00", result.endDatetime().toString());
            assertEquals(2, client.walks.get()); assertEquals(0, client.writes.get());
            assertEquals(1, executor.diagnostics().completedTasks());
            verifyNoInteractions(rsus, configs);
        }
    }

    @Test void failedSecondTableReturnsErrorInsteadOfPartialOrEmptySuccess() {
        var client = new RsuMessageForwardingServiceTest.MemoryClient();
        client.failWalk = 2;
        when(contexts.load(anyList())).thenReturn(List.of(new RsuMessageForwardingContextService.DeviceContext(7, device())));
        try (var executor = new SnmpTaskExecutor(client, new SnmpExecutorOptions(1, 10, Duration.ofSeconds(1)))) {
            assertEquals(504, assertThrows(ResponseStatusException.class, () -> service(executor).live("192.0.2.10")).getStatusCode().value());
            assertEquals(0, client.writes.get());
            verifyNoInteractions(configs);
        }
    }

    private Ntcip1218Device device() {
        return new Ntcip1218Device("192.0.2.10", new SnmpCredentials("fixture-user", "fixture-auth-pass", "fixture-privacy-pass"));
    }
}
