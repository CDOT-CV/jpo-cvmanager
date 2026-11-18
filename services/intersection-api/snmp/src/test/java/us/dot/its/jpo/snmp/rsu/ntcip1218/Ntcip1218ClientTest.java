package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import us.dot.its.jpo.snmp.client.*;
import static org.junit.jupiter.api.Assertions.*;

class Ntcip1218ClientTest {
    private static final Ntcip1218Device DEVICE = new Ntcip1218Device(new SnmpTarget("192.0.2.10",
            new SnmpCredentials("fixture-user", "fixture-auth", "fixture-private")));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-01-30T12:34:00Z"), ZoneOffset.UTC);
    private Ntcip1218Client client(SnmpClient transport) { return new Ntcip1218Client(transport, CLOCK); }

    static class MemoryAgent implements SnmpClient {
        final Map<SnmpOid, SnmpValue> data = new java.util.TreeMap<>();
        final List<List<SnmpBinding>> writes = new ArrayList<>();
        boolean lostReply, omitColumn, wrongReadback;
        @Override public List<SnmpBinding> walk(SnmpTarget target, SnmpOid root) {
            return data.entrySet().stream().filter(e -> e.getKey().isWithin(root))
                    .filter(e -> !omitColumn || !e.getKey().value().contains(".1.3."))
                    .map(e -> new SnmpBinding(e.getKey(), e.getValue())).toList();
        }
        @Override public List<SnmpBinding> set(SnmpTarget target, List<SnmpBinding> bindings) {
            writes.add(bindings);
            for (SnmpBinding binding : bindings) {
                ForwardingTable table = binding.oid().isWithin(ForwardingTable.RECEIVE.root()) ? ForwardingTable.RECEIVE : ForwardingTable.TRANSMIT;
                String[] parts = binding.oid().value().split("\\.");
                int column = Integer.parseInt(parts[parts.length - 2]), row = Integer.parseInt(parts[parts.length - 1]);
                if (column == table.statusColumn()) {
                    if (((SnmpValue.IntegerValue) binding.value()).value() == 6) {
                        for (int col = 2; col <= table.lastColumn(); col++) data.remove(table.cell(col, row));
                    } else data.put(binding.oid(), new SnmpValue.IntegerValue(1));
                } else data.put(binding.oid(), binding.value());
            }
            if (wrongReadback) data.put(ForwardingTable.RECEIVE.cell(4, 1), new SnmpValue.IntegerValue(10000));
            if (lostReply) throw new SnmpException(SnmpException.Kind.TIMEOUT, true);
            return bindings;
        }
        @Override public List<SnmpBinding> get(SnmpTarget target, List<SnmpOid> oids) {
            return oids.stream().map(oid -> new SnmpBinding(oid, data.getOrDefault(oid,
                    new SnmpValue.ExceptionValue(SnmpValue.ExceptionValue.Kind.NO_SUCH_INSTANCE)))).toList();
        }
        @Override public List<SnmpBinding> getNext(SnmpTarget target, List<SnmpOid> oids) { throw new UnsupportedOperationException(); }
    }

    @Test void matchesExpectedForwardingBindingsForAllMessagesAndSecuritySettings() throws Exception {
        // Keep the historical Python fixture unchanged; its calendar fields were 05:34.
        var fixtureClock = Clock.fixed(Instant.parse("2024-01-30T05:34:00Z"), ZoneOffset.UTC);
        var client = new Ntcip1218Client(new MemoryAgent(), fixtureClock);
        try (var input = getClass().getResourceAsStream("/ntcip-forwarding-expected.tsv")) {
            assertNotNull(input);
            for (String line : new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList()) {
                String[] fields = line.split("\t");
                var message = MessageType.valueOf(fields[0].toUpperCase(java.util.Locale.ROOT));
                var request = new ForwardingRequest(message, "198.51.100.20", fields[1].equals("1"));
                var actual = client.createBindings(request, 3);
                List<String> serialized = actual.stream().map(binding -> binding.oid().value() + ":" + switch (binding.value()) {
                    case SnmpValue.IntegerValue v -> "i:" + v.value();
                    case SnmpValue.Octets v -> "x:" + v.hex();
                    default -> throw new AssertionError();
                }).toList();
                assertEquals(List.of(fields[2].split(",")), serialized, line);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "+09:00", "-07:00", "America/Denver"})
    void forwardingSchedulesUseUtcRegardlessOfClockZone(String zoneName) {
        var zone = ZoneId.of(zoneName);
        var clock = Clock.fixed(Instant.parse("2024-03-10T23:58:49.700Z"), zone);
        var client = new Ntcip1218Client(new MemoryAgent(), clock);
        for (var message : MessageType.values()) {
            var table = message.table();
            var bindings = client.createBindings(new ForwardingRequest(message, "198.51.100.20", true), 1);
            var start = bindings.stream().filter(b -> b.oid().equals(table.cell(table.startColumn(), 1))).findFirst().orElseThrow();
            var stop = bindings.stream().filter(b -> b.oid().equals(table.cell(table.stopColumn(), 1))).findFirst().orElseThrow();
            // Literal eight-byte UTC values also catch next-day shifts in positive zones.
            assertEquals(new SnmpValue.Octets("07e8030a173a0000"), start.value(), message.name());
            assertEquals(new SnmpValue.Octets("07f2030a173a0000"), stop.value(), message.name());
        }
    }

    @Test void verifiesAddAndHighestMatchDeleteAndNoOpWhenMissing() {
        var agent = new MemoryAgent(); var client = client(agent);
        var request = new ForwardingRequest(MessageType.BSM, "198.51.100.20", true);
        assertEquals(1, client.addForwarding(DEVICE, request).value().index());
        assertEquals(2, client.addForwarding(DEVICE, request).value().index());
        assertTrue(client.deleteForwarding(DEVICE, MessageType.BSM, request.destination()).changed());
        assertEquals(1, client.readForwarding(DEVICE).size());
        assertEquals(1, client.readForwarding(DEVICE).getFirst().index());
        assertFalse(client.deleteForwarding(DEVICE, MessageType.MAP, request.destination()).changed());
        assertEquals(3, agent.writes.size());
    }

    @Test void targetedReadbackValidatesCompleteRowWithoutASecondWalk() {
        var walks = new java.util.concurrent.atomic.AtomicInteger();
        var gets = new java.util.concurrent.atomic.AtomicInteger();
        var agent = new MemoryAgent() {
            @Override public List<SnmpBinding> walk(SnmpTarget target, SnmpOid root) {
                walks.incrementAndGet();
                return super.walk(target, root);
            }
            @Override public List<SnmpBinding> get(SnmpTarget target, List<SnmpOid> oids) {
                gets.incrementAndGet();
                assertEquals(11, oids.size());
                return super.get(target, oids);
            }
        };
        var client = client(agent);
        client.addForwarding(DEVICE, new ForwardingRequest(MessageType.BSM, "198.51.100.20", true));
        assertEquals(1, walks.get()); assertEquals(1, gets.get());
        assertTrue(client.deleteForwarding(DEVICE, MessageType.BSM, "198.51.100.20").changed());
        assertEquals(2, walks.get()); assertEquals(2, gets.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void partialMissingRowAfterDeleteDoesNotConfirmAbsence(boolean selectedRow) {
        var agent = new MemoryAgent() {
            @Override public List<SnmpBinding> get(SnmpTarget target, List<SnmpOid> oids) {
                var result = new ArrayList<>(super.get(target, oids));
                if (writes.size() > 1) result.set(0, new SnmpBinding(oids.getFirst(), new SnmpValue.Octets("20")));
                return result;
            }
        };
        var client = client(agent);
        client.addForwarding(DEVICE, new ForwardingRequest(MessageType.BSM, "198.51.100.20", true));
        var failure = assertThrows(SnmpException.class, () -> {
            if (selectedRow) client.deleteForwarding(DEVICE, ForwardingTable.RECEIVE, 1, "198.51.100.20", 46800);
            else client.deleteForwarding(DEVICE, MessageType.BSM, "198.51.100.20");
        });
        assertEquals(SnmpException.Kind.INVALID_OUTPUT, failure.kind());
        assertTrue(failure.writeOutcomeUnknown());
        assertEquals(2, agent.writes.size());
    }

    @Test void incompleteReadPreventsWriteAndLostResponseNeverReplays() {
        var agent = new MemoryAgent(); var client = client(agent);
        var request = new ForwardingRequest(MessageType.BSM, "198.51.100.20", false);
        client.addForwarding(DEVICE, request);
        agent.omitColumn = true;
        assertThrows(SnmpException.class, () -> client.addForwarding(DEVICE, request));
        assertEquals(1, agent.writes.size());
        agent.omitColumn = false; agent.lostReply = true;
        var failure = assertThrows(SnmpException.class, () -> client.addForwarding(DEVICE, request));
        assertTrue(failure.writeOutcomeUnknown());
        assertEquals(2, agent.writes.size());
        assertEquals(2, client.readForwarding(DEVICE).size());
    }

    @Test void mismatchedReadbackIsNotSuccess() {
        var agent = new MemoryAgent(); agent.wrongReadback = true;
        var failure = assertThrows(SnmpException.class, () -> client(agent).addForwarding(DEVICE,
                new ForwardingRequest(MessageType.BSM, "198.51.100.20", false)));
        assertEquals(SnmpException.Kind.VERIFICATION, failure.kind()); assertTrue(failure.writeOutcomeUnknown());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "07e8001e0c223807", "07e80d1e0c223807", "07e801000c223807", "07e8021e0c223807",
            "07e8011e18223807", "07e8011e0c3c3807", "07e8011e0c223d07", "07e8011e0c22380a"
    })
    void invalidDatesRemainTypedFailuresDuringReadAndPostWriteVerification(String hex) {
        var invalidDate = new SnmpValue.Octets(hex);
        for (var message : List.of(MessageType.BSM, MessageType.MAP)) {
            var table = message.table();
            var agent = new MemoryAgent() {
                @Override public List<SnmpBinding> set(SnmpTarget target, List<SnmpBinding> bindings) {
                    var response = super.set(target, bindings);
                    data.put(table.cell(table.startColumn(), 1), invalidDate);
                    return response;
                }
            };
            var client = client(agent);
            var verification = assertThrows(SnmpException.class, () -> client.addForwarding(DEVICE,
                    new ForwardingRequest(message, "198.51.100.20", false)));
            assertEquals(SnmpException.Kind.INVALID_OUTPUT, verification.kind());
            assertTrue(verification.writeOutcomeUnknown());
            var read = assertThrows(SnmpException.class, () -> client.readForwarding(DEVICE));
            assertEquals(SnmpException.Kind.INVALID_OUTPUT, read.kind());
            assertFalse(read.writeOutcomeUnknown());
            assertEquals(1, agent.writes.size());
        }
        assertThrows(IllegalArgumentException.class, () -> NtcipDateAndTime.decode(invalidDate));
    }

    @ParameterizedTest
    @CsvSource({
            "6, -101, false", "6, -100, true", "6, -60, true", "6, -59, false", "6, 500, false",
            "7, -1, false", "7, 0, true", "7, 10, true", "7, 11, false", "7, 500, false",
            "12, -1, false", "12, 0, true", "12, 10, true", "12, 11, false", "12, 500, false"
    })
    void receiveColumnRangesRejectMalformedRowsBeforeAnotherWrite(int column, int value, boolean valid) {
        var agent = new MemoryAgent();
        var client = client(agent);
        var request = new ForwardingRequest(MessageType.BSM, "198.51.100.20", false);
        client.addForwarding(DEVICE, request);
        agent.data.put(ForwardingTable.RECEIVE.cell(column, 1), new SnmpValue.IntegerValue(value));
        if (valid) {
            assertEquals(new SnmpValue.IntegerValue(value), client.readForwarding(DEVICE).getFirst().columns().get(column));
        } else {
            var read = assertThrows(SnmpException.class, () -> client.readForwarding(DEVICE));
            assertEquals(SnmpException.Kind.INVALID_OUTPUT, read.kind());
            assertFalse(read.writeOutcomeUnknown());
            var create = assertThrows(SnmpException.class, () -> client.addForwarding(DEVICE, request));
            assertEquals(SnmpException.Kind.INVALID_OUTPUT, create.kind());
            assertFalse(create.writeOutcomeUnknown());
        }
        assertEquals(1, agent.writes.size());
    }

    @ParameterizedTest
    @EnumSource(ForwardingTable.class)
    void selectedRowDeleteRecoversMalformedTables(ForwardingTable table) {
        var message = table == ForwardingTable.RECEIVE ? MessageType.BSM : MessageType.MAP;
        var request = new ForwardingRequest(message, "198.51.100.20", false);
        for (int malformed = 0; malformed < 3; malformed++) {
            var agent = new MemoryAgent();
            var client = client(agent);
            for (var binding : client.createBindings(request, 1)) agent.data.put(binding.oid(), binding.value());
            if (malformed == 0) {
                // An incomplete notReady row still has enough destination evidence for deletion.
                agent.data.remove(table.cell(2, 1));
                agent.data.put(table.cell(table.statusColumn(), 1), new SnmpValue.IntegerValue(3));
            } else if (malformed == 1) {
                agent.data.put(table.cell(5, 1), new SnmpValue.IntegerValue(99));
            } else {
                agent.data.put(table.cell(table.startColumn(), 1), new SnmpValue.Octets("07e8011e0c2200002b0000"));
            }

            assertEquals(SnmpException.Kind.INVALID_OUTPUT,
                    assertThrows(SnmpException.class, () -> client.readForwarding(DEVICE)).kind());
            assertThrows(SnmpException.class, () -> client.addForwarding(DEVICE, request));
            assertTrue(agent.writes.isEmpty());

            assertTrue(client.deleteForwarding(DEVICE, table, 1, request.destination(), message.port()).changed());
            assertEquals(1, agent.writes.size());
            assertEquals(List.of(), client.readForwarding(DEVICE));
            assertEquals(1, client.addForwarding(DEVICE, request).value().index());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2001:db8::1", "device.example", ""})
    void selectedRowDeleteMatchesExistingDestinationTextWithoutAllowingItOnCreate(String destination) {
        for (var table : ForwardingTable.values()) {
            var agent = new MemoryAgent();
            var client = client(agent);
            var message = table == ForwardingTable.RECEIVE ? MessageType.BSM : MessageType.MAP;
            for (var binding : client.createBindings(new ForwardingRequest(message, "198.51.100.20", false), 1)) {
                agent.data.put(binding.oid(), binding.value());
            }
            agent.data.put(table.cell(3, 1), SnmpValue.Octets.text(destination));
            assertEquals(destination, client.readForwarding(DEVICE).getFirst().destination());
            assertTrue(client.deleteForwarding(DEVICE, table, 1, destination, message.port()).changed());
            assertEquals(1, agent.writes.size());
        }
        assertThrows(IllegalArgumentException.class,
                () -> new ForwardingRequest(MessageType.MAP, destination, false));
    }

    @Test void selectedRowDestinationUsesByteLengthAndExactOctets() {
        var agent = new MemoryAgent();
        var client = client(agent);
        var table = ForwardingTable.TRANSMIT;
        String destination = "\u00e9".repeat(32);
        agent.data.put(table.cell(3, 1), SnmpValue.Octets.text(destination));
        agent.data.put(table.cell(4, 1), new SnmpValue.IntegerValue(44920));
        assertTrue(client.deleteForwarding(DEVICE, table, 1, destination, 44920).changed());
        for (String invalid : java.util.Arrays.asList(null, "x".repeat(65), "\u00e9".repeat(33))) {
            assertThrows(IllegalArgumentException.class,
                    () -> client.deleteForwarding(DEVICE, table, 1, invalid, 44920));
        }
        assertEquals(1, agent.writes.size());
    }

    @Test void selectedMalformedRowStillRequiresReadableMatchingDestinationAndPort() {
        var table = ForwardingTable.RECEIVE;
        var agent = new MemoryAgent();
        var client = client(agent);
        agent.data.put(table.cell(3, 1), SnmpValue.Octets.text("198.51.100.20"));
        agent.data.put(table.cell(4, 1), new SnmpValue.IntegerValue(46800));
        var stale = assertThrows(SnmpException.class,
                () -> client.deleteForwarding(DEVICE, table, 1, "198.51.100.21", 46800));
        assertEquals(SnmpException.Kind.VERIFICATION, stale.kind());
        assertFalse(stale.writeOutcomeUnknown());
        assertThrows(SnmpException.class,
                () -> client.deleteForwarding(DEVICE, table, 1, "198.51.100.20", 46801));

        agent.data.remove(table.cell(3, 1));
        var missing = assertThrows(SnmpException.class,
                () -> client.deleteForwarding(DEVICE, table, 1, "198.51.100.20", 46800));
        assertEquals(SnmpException.Kind.INVALID_OUTPUT, missing.kind());
        assertFalse(missing.writeOutcomeUnknown());
        agent.data.put(table.cell(3, 1), SnmpValue.Octets.text("198.51.100.20"));
        agent.data.put(table.cell(4, 1), SnmpValue.Octets.text("46800"));
        assertThrows(SnmpException.class,
                () -> client.deleteForwarding(DEVICE, table, 1, "198.51.100.20", 46800));
        assertTrue(agent.writes.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 80, 65536})
    void selectedRowDeleteCanRemoveAnOutOfRangeStoredPort(int port) {
        var agent = new MemoryAgent();
        var client = client(agent);
        var table = ForwardingTable.TRANSMIT;
        for (var binding : client.createBindings(new ForwardingRequest(MessageType.MAP, "198.51.100.20", false), 1)) {
            agent.data.put(binding.oid(), binding.value());
        }
        agent.data.put(table.cell(4, 1), new SnmpValue.IntegerValue(port));
        assertThrows(SnmpException.class, () -> client.readForwarding(DEVICE));
        assertTrue(client.deleteForwarding(DEVICE, table, 1, "198.51.100.20", port).changed());
        assertEquals(1, agent.writes.size());
    }

    @Test void selectedRecoveryDeleteDoesNotReplayALostSetReply() {
        var agent = new MemoryAgent();
        var table = ForwardingTable.RECEIVE;
        agent.data.put(table.cell(3, 1), SnmpValue.Octets.text("198.51.100.20"));
        agent.data.put(table.cell(4, 1), new SnmpValue.IntegerValue(46800));
        agent.lostReply = true;
        var client = client(agent);
        var failure = assertThrows(SnmpException.class,
                () -> client.deleteForwarding(DEVICE, table, 1, "198.51.100.20", 46800));
        assertTrue(failure.writeOutcomeUnknown());
        assertEquals(1, agent.writes.size());
        assertTrue(client.readForwarding(DEVICE).isEmpty());
    }

    @Test void helperInstancesCoordinateConcurrentCreatesForTheSameEndpoint() throws Exception {
        var firstRead = new CountDownLatch(1);
        var releaseRead = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var secondRead = new CountDownLatch(1);
        var reads = new AtomicInteger();
        var agent = new MemoryAgent() {
            @Override public List<SnmpBinding> walk(SnmpTarget target, SnmpOid root) {
                var result = super.walk(target, root);
                if (reads.incrementAndGet() == 1) {
                    firstRead.countDown();
                    try {
                        if (!releaseRead.await(5, TimeUnit.SECONDS)) throw new AssertionError("First read was not released");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                } else secondRead.countDown();
                return result;
            }
        };
        var request = new ForwardingRequest(MessageType.MAP, "198.51.100.20", false);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> client(agent).addForwarding(DEVICE, request));
            try {
                assertTrue(firstRead.await(5, TimeUnit.SECONDS));
                var second = workers.submit(() -> {
                    secondStarted.countDown();
                    return client(agent).addForwarding(DEVICE, request);
                });
                assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
                assertFalse(secondRead.await(150, TimeUnit.MILLISECONDS));
                releaseRead.countDown();
                assertEquals(1, first.get(5, TimeUnit.SECONDS).value().index());
                assertEquals(2, second.get(5, TimeUnit.SECONDS).value().index());
            } finally {
                releaseRead.countDown();
            }
        }
        assertEquals(2, agent.writes.size());
    }

    @ParameterizedTest
    @CsvSource({"8.10.1.4, 255, true", "8.10.1.4, 256, false", "8.10.1.5, 65535, true", "8.10.1.5, 65536, false"})
    void certificateHourColumnsUseTheirOwnBounds(String suffix, int value, boolean valid) {
        var agent = new MemoryAgent();
        agent.data.put(new SnmpOid("1.3.6.1.4.1.1206.4.2.18." + suffix + ".1"), new SnmpValue.IntegerValue(value));
        var client = client(agent);
        java.util.function.Supplier<Map<Integer, Integer>> read = suffix.endsWith(".4")
                ? () -> client.readCertificateExpirationHours(DEVICE) : () -> client.readCertificateRequestHours(DEVICE);
        if (valid) assertEquals(Map.of(1, value), read.get());
        else assertEquals(SnmpException.Kind.INVALID_OUTPUT, assertThrows(SnmpException.class, read::get).kind());
    }

    @Test void leapSecondDecodesAsTheLastRepresentableSecondWithoutHidingInvalidSeconds() {
        var leap = new SnmpValue.Octets("07e80c1f173b3c07");
        assertEquals(LocalDateTime.of(2024, 12, 31, 23, 59, 59, 700_000_000), NtcipDateAndTime.decode(leap));
        assertThrows(IllegalArgumentException.class,
                () -> NtcipDateAndTime.decode(new SnmpValue.Octets("07e80c1f173b3d07")));
    }

    @Test void profilesAreValidatedBeforeBatchCanRun() {
        assertEquals(DEVICE, new Ntcip1218Device(DEVICE.target()));
        assertEquals(DEVICE, new Ntcip1218Device(DEVICE.target().address(), DEVICE.target().credentials()));
        assertEquals(new SnmpTarget(DEVICE.target().address(), 1161, DEVICE.target().credentials()),
                new Ntcip1218Device(DEVICE.target().address(), 1161, DEVICE.target().credentials()).target());
        assertThrows(NullPointerException.class, () -> new Ntcip1218Device(null));
        assertEquals(List.of(DEVICE), Ntcip1218Device.preflight(List.of(
                new Ntcip1218Device.ProfiledTarget(DEVICE.target(), "1218"))));
        assertThrows(IllegalArgumentException.class, () -> Ntcip1218Device.preflight(List.of()));
        for (String profile : new String[]{"41", "other", null}) {
            assertThrows(IllegalArgumentException.class, () -> Ntcip1218Device.preflight(List.of(
                    new Ntcip1218Device.ProfiledTarget(DEVICE.target(), "1218"), new Ntcip1218Device.ProfiledTarget(DEVICE.target(), profile))));
        }
    }

    @Test void sameDeviceConcurrentAddsCannotAllocateTheSameRow() throws Exception {
        var agent = new MemoryAgent(); var client = client(agent);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 20).mapToObj(i -> executor.submit(() -> client.addForwarding(DEVICE,
                    new ForwardingRequest(MessageType.MAP, "198.51.100.20", false)))).toList();
            var indices = new java.util.HashSet<Integer>();
            for (var task : tasks) assertTrue(indices.add(task.get(10, TimeUnit.SECONDS).value().index()));
        }
        assertEquals(20, agent.writes.size());
    }

    @Test void explicitPortsAreCreatedVerifiedAndMatchedDuringDeletion() {
        var agent = new MemoryAgent();
        var client = client(agent);
        var preset = new ForwardingRequest(MessageType.BSM, "198.51.100.20", false);
        var custom = new ForwardingRequest(MessageType.BSM, preset.destination(), 44990, true);
        assertEquals(46800, preset.port());
        assertEquals(44990, client.addForwarding(DEVICE, custom).value().port());
        client.addForwarding(DEVICE, preset);
        client.addForwarding(DEVICE, custom);
        assertTrue(client.deleteForwarding(DEVICE, MessageType.BSM, preset.destination()).changed());
        assertEquals(List.of(1, 3), client.readForwarding(DEVICE).stream().map(ForwardingRow::index).toList());
        assertFalse(client.deleteForwarding(DEVICE, MessageType.BSM, preset.destination()).changed());
        assertTrue(client.deleteForwarding(DEVICE, MessageType.BSM, preset.destination(), 44990).changed());
        assertEquals(List.of(1), client.readForwarding(DEVICE).stream().map(ForwardingRow::index).toList());
        assertTrue(client.deleteForwarding(DEVICE, MessageType.BSM, preset.destination(), 44990).changed());
        assertEquals(6, agent.writes.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1023, 65536})
    void explicitCreateAndPresetDeletePortsMustFitTheMib(int port) {
        var agent = new MemoryAgent();
        assertThrows(IllegalArgumentException.class,
                () -> new ForwardingRequest(MessageType.BSM, "198.51.100.20", port, false));
        assertThrows(IllegalArgumentException.class,
                () -> client(agent).deleteForwarding(DEVICE, MessageType.BSM, "198.51.100.20", port));
        assertTrue(agent.writes.isEmpty());
    }

    @Test void endpointsThatCollidedInTheOldStripesCanProgressIndependently() throws Exception {
        var firstDevice = new Ntcip1218Device("192.0.2.1", DEVICE.target().credentials());
        var secondDevice = new Ntcip1218Device("192.0.2.166", DEVICE.target().credentials());
        assertEquals(Math.floorMod(java.util.Objects.hash(firstDevice.target().address(), 161), 256),
                Math.floorMod(java.util.Objects.hash(secondDevice.target().address(), 161), 256));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var slow = new MemoryAgent() {
            @Override public List<SnmpBinding> walk(SnmpTarget target, SnmpOid root) {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Slow read was not released");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                return super.walk(target, root);
            }
        };
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> client(slow).addForwarding(firstDevice,
                    new ForwardingRequest(MessageType.MAP, "198.51.100.20", false)));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var second = workers.submit(() -> client(new MemoryAgent()).addForwarding(secondDevice,
                        new ForwardingRequest(MessageType.MAP, "198.51.100.20", false)));
                assertEquals(1, second.get(2, TimeUnit.SECONDS).value().index());
            } finally {
                release.countDown();
            }
            assertEquals(1, first.get(5, TimeUnit.SECONDS).value().index());
        }
    }

    @Test void scalarReadsPreserveUnknownCoordinatesAndErrors() {
        var agent = new MemoryAgent();
        agent.data.put(new SnmpOid("1.3.6.1.4.1.1206.4.2.18.6.6.0"), new SnmpValue.IntegerValue(900000001));
        agent.data.put(new SnmpOid("1.3.6.1.4.1.1206.4.2.18.6.7.0"), new SnmpValue.IntegerValue(-1050342786));
        var location = client(agent).readLocation(DEVICE);
        assertTrue(location.latitude().isEmpty()); assertEquals(-105.0342786, location.longitude().orElseThrow());
        assertThrows(SnmpException.class, () -> client(agent).readStatus(DEVICE));
    }

    @Test void dateRoundTripAndLeapDay() {
        var date = LocalDateTime.of(2024, 2, 29, 12, 34, 56, 700_000_000);
        assertEquals(date, NtcipDateAndTime.decode(NtcipDateAndTime.encode(date)));
        assertThrows(IllegalArgumentException.class, () -> NtcipDateAndTime.decode(new SnmpValue.Octets("00")));
    }
}
