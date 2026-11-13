package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import us.dot.its.jpo.ode.api.config.SnmpProperties;
import us.dot.its.jpo.ode.api.models.devices.management.MessageForwardingConfiguration.Table;
import us.dot.its.jpo.ode.api.models.postgres.tables.*;
import us.dot.its.jpo.ode.api.repositories.*;
import us.dot.its.jpo.snmp.client.SnmpCredentials;
import us.dot.its.jpo.snmp.client.SnmpValue;
import us.dot.its.jpo.ode.api.models.devices.management.DeleteMessageForwardingRowRequest;
import us.dot.its.jpo.ode.api.models.devices.management.CreateMessageForwardingRequest;
import us.dot.its.jpo.ode.api.models.devices.management.MessageForwardingResult.DatabaseSync;
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

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-15T16:00:00Z", "2026-07-15T16:00:00Z"})
    void cachedReadNeedsNeitherCredentialsNorDeviceConnectivityAndPreservesLegacyRows(String timestamp) throws Exception {
        var executor = mock(SnmpTaskExecutor.class);
        var ip = InetAddress.getByName("192.0.2.10");
        when(rsus.findByIpv4Address(ip)).thenReturn(new Rsu());
        var type = new SnmpMsgfwdType(); type.setName("rsuDsrcFwd");
        var id = new SnmpMsgfwdConfigId(); id.setSnmpIndex(5);
        var row = new SnmpMsgfwdConfig(); row.setId(id); row.setMsgfwdType(type);
        row.setMessageType("map"); row.setDestIpv4(InetAddress.getByName("198.51.100.3")); row.setDestPort(44920);
        var start = Instant.parse(timestamp);
        row.setStartDatetime(start); row.setEndDatetime(start.plusSeconds(3600));
        row.setActive(true); row.setSecurity(false);
        when(configs.findForwardingByIpv4Address(ip)).thenReturn(List.of(row));
        var result = service(executor).cached("192.0.2.10").getFirst();
        assertEquals(Table.DSRC, result.table()); assertEquals(5, result.index());
        assertEquals("MAP", result.messageType()); assertEquals("198.51.100.3", result.destinationIp());
        assertEquals(start.atOffset(ZoneOffset.UTC), result.startDatetime());
        assertEquals(start.plusSeconds(3600).atOffset(ZoneOffset.UTC), result.endDatetime());
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

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-15T16:00:00Z", "2026-07-15T16:00:00Z"})
    void liveReadUsesTrackedExecutorAndNeverWritesDeviceOrDatabase(String timestamp) {
        var clock = Clock.fixed(Instant.parse(timestamp), ZoneOffset.UTC);
        var client = new RsuMessageForwardingServiceTest.MemoryClient();
        var device = device();
        new Ntcip1218Client(client, clock).addForwarding(device,
                new ForwardingRequest(MessageType.MAP, "198.51.100.3", true));
        client.writes.set(0); client.walks.set(0);
        when(contexts.load(List.of("192.0.2.10"))).thenReturn(List.of(new RsuMessageForwardingContextService.DeviceContext(7, device)));
        try (var executor = new SnmpTaskExecutor(client, new SnmpExecutorOptions(1, 10, Duration.ofSeconds(1)))) {
            var properties = new SnmpProperties();
            var service = new RsuMessageForwardingReadService(rsus, configs, contexts, executor, properties, clock);
            var result = service.live("192.0.2.10").getFirst();
            assertEquals(Table.TRANSMIT, result.table()); assertEquals("MAP", result.messageType());
            assertEquals(44920, result.destinationPort()); assertTrue(result.includeSecurity());
            var start = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            assertEquals(start, result.startDatetime());
            assertEquals(start.plusYears(10), result.endDatetime());
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

    @Test void malformedRowsRemainVisibleAndCanBeDeletedWithoutPersistingDiagnosticData() {
        var client = new RsuMessageForwardingServiceTest.MemoryClient();
        var device = device();
        var helper = new Ntcip1218Client(client, CLOCK);
        helper.addForwarding(device, new ForwardingRequest(MessageType.MAP, "198.51.100.3", true));
        helper.addForwarding(device, new ForwardingRequest(MessageType.MAP, "198.51.100.4", false));
        client.data.remove(ForwardingTable.TRANSMIT.cell(6, 1));
        // Recovery must support even an invalid stored port; it is only a comparison guard.
        client.data.put(ForwardingTable.TRANSMIT.cell(4, 1), new SnmpValue.IntegerValue(80));
        client.walks.set(0);
        when(contexts.load(anyList())).thenReturn(List.of(new RsuMessageForwardingContextService.DeviceContext(7, device)));
        try (var executor = new SnmpTaskExecutor(client, new SnmpExecutorOptions(1, 10, Duration.ofSeconds(1)))) {
            var rows = service(executor).live("192.0.2.10");
            assertEquals(2, client.walks.get(), "Diagnostics must not double the table walks");
            assertEquals(2, rows.size());
            var bad = rows.getFirst();
            assertNotNull(bad.validationError());
            assertEquals(1, bad.index());
            assertEquals("198.51.100.3", bad.destinationIp());
            assertEquals(80, bad.destinationPort());
            assertNull(bad.startDatetime());
            assertNull(bad.active());
            assertNull(bad.includeSecurity());
            assertNull(rows.getLast().validationError());
            assertEquals("198.51.100.4", rows.getLast().destinationIp());
            assertTrue(rows.getLast().active());
            var snapshots = mock(RsuMessageForwardingSnapshotService.class);
            var changes = new RsuMessageForwardingService(contexts, snapshots, executor,
                    new SnmpProperties(), new Ntcip1218ForwardingTasks(CLOCK));
            var deleted = changes.deleteRow("192.0.2.10", ForwardingTable.TRANSMIT, bad.index(),
                    new DeleteMessageForwardingRowRequest(
                            bad.destinationIp(), bad.destinationPort())).get("192.0.2.10");
            assertEquals(true, deleted.changed());
            verify(snapshots).replace(eq(7), eq("192.0.2.10"), argThat(snapshot -> snapshot.size() == 1));
            verifyNoInteractions(configs);
        }
    }

    @Test void anotherMalformedRowBlocksCreationAndPersistenceButNotVerifiedCleanup() {
        var client = new RsuMessageForwardingServiceTest.MemoryClient();
        var device = device();
        var helper = new Ntcip1218Client(client, CLOCK);
        for (int index = 1; index <= 2; index++) {
            helper.addForwarding(device, new ForwardingRequest(MessageType.MAP, "198.51.100.3", false));
        }
        for (int index = 1; index <= 2; index++) client.data.remove(ForwardingTable.TRANSMIT.cell(6, index));
        when(contexts.load(anyList())).thenReturn(List.of(new RsuMessageForwardingContextService.DeviceContext(7, device)));
        var snapshots = mock(RsuMessageForwardingSnapshotService.class);
        try (var executor = new SnmpTaskExecutor(client, new SnmpExecutorOptions(1, 10, Duration.ofSeconds(1)))) {
            var changes = new RsuMessageForwardingService(contexts, snapshots, executor,
                    new SnmpProperties(), new Ntcip1218ForwardingTasks(CLOCK));
            var create = new CreateMessageForwardingRequest();
            create.setRsuIps(List.of("192.0.2.10")); create.setMessageType(MessageType.MAP);
            create.setDestinationIp("198.51.100.3"); create.setIncludeSecurity(false);
            assertEquals(SnmpTaskResult.Status.FAILED, changes.create(create).get("192.0.2.10").execution().status());
            assertEquals(2, client.writes.get(), "Creation must not write through malformed table data");
            var removed = changes.deleteRow("192.0.2.10", ForwardingTable.TRANSMIT, 1,
                    new DeleteMessageForwardingRowRequest("198.51.100.3", 44920)).get("192.0.2.10");
            assertEquals(true, removed.changed());
            assertEquals(SnmpTaskResult.WriteState.VERIFIED, removed.writeState());
            assertEquals(DatabaseSync.NOT_UPDATED,
                    removed.databaseSync());
            assertEquals(2, service(executor).live("192.0.2.10").getFirst().index());
            verifyNoInteractions(snapshots, configs);
        }
    }

    @Test void unreadableGuardsAndInvalidDatesAreReportedWithoutInventedValues() {
        var client = new RsuMessageForwardingServiceTest.MemoryClient();
        new Ntcip1218Client(client, CLOCK).addForwarding(device(),
                new ForwardingRequest(MessageType.MAP, "198.51.100.3", true));
        client.data.remove(ForwardingTable.TRANSMIT.cell(3, 1));
        client.data.remove(ForwardingTable.TRANSMIT.cell(4, 1));
        when(contexts.load(anyList())).thenReturn(List.of(new RsuMessageForwardingContextService.DeviceContext(7, device())));
        try (var executor = new SnmpTaskExecutor(client, new SnmpExecutorOptions(1, 10, Duration.ofSeconds(1)))) {
            var row = service(executor).live("192.0.2.10").getFirst();
            assertNotNull(row.validationError());
            assertNull(row.destinationIp());
            assertNull(row.destinationPort());
            client.data.put(ForwardingTable.TRANSMIT.cell(3, 1), SnmpValue.Octets.text("198.51.100.3"));
            client.data.put(ForwardingTable.TRANSMIT.cell(4, 1), new SnmpValue.IntegerValue(44920));
            client.data.put(ForwardingTable.TRANSMIT.cell(6, 1), SnmpValue.Octets.bytes(new byte[11]));
            assertNotNull(service(executor).live("192.0.2.10").getFirst().validationError());
            client.failWalk = client.walks.get() + 2;
            assertEquals(504, assertThrows(ResponseStatusException.class,
                    () -> service(executor).live("192.0.2.10")).getStatusCode().value());
            verifyNoInteractions(configs);
        }
    }

    private Ntcip1218Device device() {
        return new Ntcip1218Device("192.0.2.10", new SnmpCredentials("fixture-user", "fixture-auth-pass", "fixture-privacy-pass"));
    }
}
