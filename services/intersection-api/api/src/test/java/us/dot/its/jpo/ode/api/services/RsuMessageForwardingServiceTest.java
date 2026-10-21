package us.dot.its.jpo.ode.api.services;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.server.ResponseStatusException;
import us.dot.its.jpo.ode.api.config.SnmpProperties;
import us.dot.its.jpo.ode.api.models.devices.management.CreateMessageForwardingRequest;
import us.dot.its.jpo.ode.api.models.devices.management.MessageForwardingResult.DatabaseSync;
import us.dot.its.jpo.ode.api.services.RsuMessageForwardingContextService.DeviceContext;
import us.dot.its.jpo.snmp.client.*;
import us.dot.its.jpo.snmp.execution.*;
import us.dot.its.jpo.snmp.rsu.ntcip1218.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RsuMessageForwardingServiceTest {
    private final RsuMessageForwardingContextService contexts = mock(RsuMessageForwardingContextService.class);
    private final RsuMessageForwardingSnapshotService snapshots = mock(RsuMessageForwardingSnapshotService.class);
    private final Ntcip1218Device device = new Ntcip1218Device("192.0.2.10",
            new SnmpCredentials("fixture-user", "fixture-auth-pass", "fixture-privacy-pass"));

    private CreateMessageForwardingRequest request() {
        var request = new CreateMessageForwardingRequest();
        request.setRsuIps(List.of(device.target().address()));
        request.setMessageType(MessageType.MAP);
        request.setDestinationIp("198.51.100.3");
        request.setIncludeSecurity(true);
        return request;
    }

    private RsuMessageForwardingService service(SnmpTaskExecutor executor) {
        when(contexts.load(anyList())).thenReturn(List.of(new DeviceContext(7, device)));
        return new RsuMessageForwardingService(contexts, snapshots, executor, new SnmpProperties(), new Ntcip1218ForwardingTasks(Clock.systemUTC()));
    }

    private SnmpTaskExecutor executor(MemoryClient client) {
        return new SnmpTaskExecutor(client, new SnmpExecutorOptions(1, 10, Duration.ofSeconds(1)));
    }

    @Test void createsMapUsingExpectedUdpPortAndSecurityThenDeletesAndHandlesNoOp() {
        var client = new MemoryClient();
        try (var executor = executor(client)) {
            var service = service(executor);
            var created = service.create(request()).get(device.target().address());
            assertEquals(SnmpTaskResult.Status.SUCCEEDED, created.execution().status());
            assertEquals(SnmpTaskResult.WriteState.VERIFIED, created.writeState());
            assertEquals(DatabaseSync.UPDATED, created.databaseSync());
            assertEquals(true, created.changed());
            assertEquals(new SnmpValue.IntegerValue(44920), client.data.get(ForwardingTable.TRANSMIT.cell(4, 1)));
            assertEquals(new SnmpValue.IntegerValue(2), client.data.get(ForwardingTable.TRANSMIT.cell(5, 1)));
            assertEquals(new SnmpValue.IntegerValue(1), client.data.get(ForwardingTable.TRANSMIT.cell(8, 1)));
            assertEquals(MessageType.MAP.psid(), client.data.get(ForwardingTable.TRANSMIT.cell(2, 1)));
            var deleted = service.delete(request()).get(device.target().address());
            assertEquals(true, deleted.changed());
            assertEquals(SnmpTaskResult.WriteState.VERIFIED, deleted.writeState());
            assertTrue(client.data.isEmpty());
            var noOp = service.delete(request()).get(device.target().address());
            assertEquals(false, noOp.changed());
            assertEquals(SnmpTaskResult.Status.SUCCEEDED, noOp.execution().status());
            assertEquals(SnmpTaskResult.WriteState.NOT_SUBMITTED, noOp.writeState());
            assertEquals(2, client.writes.get());
            verify(snapshots, times(3)).replace(eq(7), eq(device.target().address()), anyList());
        }
    }

    @Test void lostWriteReplyIsNotRetriedOrPersistedAsConfirmed() {
        var client = new MemoryClient();
        client.loseWrite = true;
        try (var executor = executor(client)) {
            var result = service(executor).create(request()).get(device.target().address());
            assertEquals(SnmpTaskResult.WriteState.UNKNOWN, result.writeState());
            assertEquals(DatabaseSync.NOT_UPDATED, result.databaseSync());
            assertNull(result.changed());
            assertEquals(1, client.writes.get());
            verifyNoInteractions(snapshots);
        }
    }

    @Test void incompleteSnapshotPreservesDatabaseAndConfirmedWriteEvidence() {
        var client = new MemoryClient();
        client.failWalk = 3;
        try (var executor = executor(client)) {
            var result = service(executor).create(request()).get(device.target().address());
            assertEquals(SnmpTaskResult.Status.TIMED_OUT, result.execution().status());
            assertEquals(SnmpTaskResult.WriteState.VERIFIED, result.writeState());
            assertEquals(DatabaseSync.NOT_UPDATED, result.databaseSync());
            assertEquals(true, result.changed());
            verifyNoInteractions(snapshots);
        }
    }

    static Stream<Arguments> snapshotFailures() {
        return Stream.of(
                Arguments.of(new TransactionSystemException("private transaction detail",
                        new SQLException("private SQL detail", "23505", 0)), "SQLException", "23505", "0"),
                Arguments.of(new TransactionSystemException("private transaction detail",
                        new SQLException("private SQL detail", "08006", 101, new IOException("private connection detail"))),
                        "IOException", "08006", "101"),
                Arguments.of(new IllegalStateException("private application detail"),
                        "IllegalStateException", "unknown", "unknown"),
                Arguments.of(new TransactionSystemException("private transaction detail",
                        new SQLException("private SQL detail", "private\ninvalid state", 0)), "SQLException", "unknown", "0"));
    }

    @ParameterizedTest
    @MethodSource("snapshotFailures")
    @ExtendWith(OutputCaptureExtension.class)
    void databaseFailureIsLoggedWithoutPrivateDetailsAndPreservesVerifiedDeviceOutcome(
            RuntimeException failure, String causeType, String sqlState, String errorCode, CapturedOutput output) {
        var client = new MemoryClient();
        doThrow(failure).when(snapshots).replace(anyInt(), anyString(), anyList());
        try (var executor = executor(client)) {
            var result = service(executor).create(request()).get(device.target().address());
            assertEquals(SnmpTaskResult.Status.FAILED, result.execution().status());
            assertEquals(SnmpTaskResult.WriteState.VERIFIED, result.writeState());
            assertEquals(DatabaseSync.FAILED, result.databaseSync());
            assertEquals(true, result.changed());
            var diagnostic = "RSU forwarding snapshot replacement failed [ip=192.0.2.10 rsuId=7 error="
                    + failure.getClass().getSimpleName() + " cause=" + causeType + " sqlState=" + sqlState
                    + " errorCode=" + errorCode + "]";
            assertEquals(1, output.getAll().lines().filter(line -> line.contains(diagnostic)).count());
            assertFalse(output.getAll().contains("private"));
        }
    }

    @Test void rejectsInvalidAndDuplicateAddressesBeforeLoadingCredentials() {
        var request = request();
        request.setRsuIps(List.of("localhost"));
        assertThrows(ResponseStatusException.class, () -> RsuMessageForwardingService.validateRequest(request));
        request.setRsuIps(List.of("192.0.2.10", "192.0.2.10"));
        assertThrows(ResponseStatusException.class, () -> RsuMessageForwardingService.validateRequest(request));
        verifyNoInteractions(contexts, snapshots);
    }

    @Test void failedBatchPreflightSendsNoCommands() {
        var client = new MemoryClient();
        try (var executor = executor(client)) {
            var service = new RsuMessageForwardingService(contexts, snapshots, executor, new SnmpProperties(), new Ntcip1218ForwardingTasks(Clock.systemUTC()));
            when(contexts.load(anyList())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST));
            assertThrows(ResponseStatusException.class, () -> service.create(request()));
            assertEquals(0, client.walks.get());
            assertEquals(0, client.writes.get());
        }
    }

    @Test void offlineDeviceDoesNotHideAnotherDevicesSuccess() {
        var client = new MemoryClient();
        client.offlineAddress = device.target().address();
        var other = new Ntcip1218Device("192.0.2.11", device.target().credentials());
        try (var executor = executor(client)) {
            var service = service(executor);
            when(contexts.load(anyList())).thenReturn(List.of(new DeviceContext(7, device), new DeviceContext(8, other)));
            var request = request();
            request.setRsuIps(List.of(device.target().address(), other.target().address()));
            var results = service.create(request);
            assertEquals(SnmpTaskResult.Status.TIMED_OUT, results.get(device.target().address()).execution().status());
            assertEquals(DatabaseSync.NOT_UPDATED, results.get(device.target().address()).databaseSync());
            assertEquals(SnmpTaskResult.Status.SUCCEEDED, results.get(other.target().address()).execution().status());
            assertEquals(DatabaseSync.UPDATED, results.get(other.target().address()).databaseSync());
            verify(snapshots, never()).replace(eq(7), anyString(), anyList());
        }
    }

    /** Deterministic generic client; exercises the real executor and NTCIP procedure without hardware. */
    static class MemoryClient implements SnmpClient {
        final Map<SnmpOid, SnmpValue> data = new TreeMap<>();
        final AtomicInteger writes = new AtomicInteger();
        final AtomicInteger walks = new AtomicInteger();
        boolean loseWrite;
        int failWalk;
        String offlineAddress;
        @Override public List<SnmpBinding> walk(SnmpTarget target, SnmpOid root) {
            if (target.address().equals(offlineAddress)) throw new SnmpException(SnmpException.Kind.TIMEOUT, false);
            if (walks.incrementAndGet() == failWalk) throw new SnmpException(SnmpException.Kind.TIMEOUT, false);
            return data.entrySet().stream().filter(e -> e.getKey().isWithin(root))
                    .map(e -> new SnmpBinding(e.getKey(), e.getValue())).toList();
        }
        @Override public List<SnmpBinding> set(SnmpTarget target, List<SnmpBinding> bindings) {
            writes.incrementAndGet();
            for (var binding : bindings) {
                var table = binding.oid().isWithin(ForwardingTable.TRANSMIT.root()) ? ForwardingTable.TRANSMIT : ForwardingTable.RECEIVE;
                if (binding.oid().equals(table.cell(table.statusColumn(), 1))) {
                    if (binding.value().equals(new SnmpValue.IntegerValue(6))) data.clear();
                    else data.put(binding.oid(), new SnmpValue.IntegerValue(1));
                } else data.put(binding.oid(), binding.value());
            }
            if (loseWrite) throw new SnmpException(SnmpException.Kind.TIMEOUT, true);
            return bindings;
        }
        @Override public List<SnmpBinding> get(SnmpTarget t, List<SnmpOid> o) {
            return o.stream().map(oid -> new SnmpBinding(oid, data.getOrDefault(oid,
                    new SnmpValue.ExceptionValue(SnmpValue.ExceptionValue.Kind.NO_SUCH_INSTANCE)))).toList();
        }
        @Override public List<SnmpBinding> getNext(SnmpTarget t, List<SnmpOid> o) { throw new UnsupportedOperationException(); }
        @Override public SnmpClient scoped(SnmpCallContext context) {
            return new SnmpClient() {
                @Override public List<SnmpBinding> walk(SnmpTarget t, SnmpOid o) {
                    context.beforeRequest(false); return MemoryClient.this.walk(t, o);
                }
                @Override public List<SnmpBinding> set(SnmpTarget t, List<SnmpBinding> b) {
                    context.beforeRequest(true); return MemoryClient.this.set(t, b);
                }
                @Override public List<SnmpBinding> get(SnmpTarget t, List<SnmpOid> o) {
                    context.beforeRequest(false); return MemoryClient.this.get(t, o);
                }
                @Override public List<SnmpBinding> getNext(SnmpTarget t, List<SnmpOid> o) { throw new UnsupportedOperationException(); }
            };
        }
    }
}
