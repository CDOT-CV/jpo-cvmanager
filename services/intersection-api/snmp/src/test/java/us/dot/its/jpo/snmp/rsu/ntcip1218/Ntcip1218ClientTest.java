package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import us.dot.its.jpo.snmp.client.*;
import static org.junit.jupiter.api.Assertions.*;

class Ntcip1218ClientTest {
    private static final Ntcip1218Device DEVICE = new Ntcip1218Device(new SnmpTarget("192.0.2.10",
            new SnmpCredentials("fixture-user", "fixture-auth", "fixture-private")));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-01-30T12:34:00Z"), ZoneOffset.UTC);
    private Ntcip1218Client client(SnmpClient transport) { return new Ntcip1218Client(transport, CLOCK, ZoneOffset.ofHours(-7)); }

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

    @Test void matchesPythonGoldenSequencesForAllMessagesAndSecuritySettings() throws Exception {
        var client = client(new MemoryAgent());
        try (var input = getClass().getResourceAsStream("/ntcip-golden.tsv")) {
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

    @Test void verifiesAddAndHighestMatchDeleteAndNoOpWhenMissing() {
        var agent = new MemoryAgent(); var client = client(agent);
        var request = new ForwardingRequest(MessageType.BSM, "198.51.100.20", true);
        assertEquals(1, client.addForwarding(DEVICE, request).index());
        assertEquals(2, client.addForwarding(DEVICE, request).index());
        assertTrue(client.deleteForwarding(DEVICE, MessageType.BSM, request.destination()));
        assertEquals(1, client.readForwarding(DEVICE).size());
        assertEquals(1, client.readForwarding(DEVICE).getFirst().index());
        assertFalse(client.deleteForwarding(DEVICE, MessageType.MAP, request.destination()));
        assertEquals(3, agent.writes.size());
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
            for (var task : tasks) assertTrue(indices.add(task.get(10, TimeUnit.SECONDS).index()));
        }
        assertEquals(20, agent.writes.size());
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
