package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.dot.its.jpo.snmp.client.*;
import us.dot.its.jpo.snmp.internal.OperationLog;
import static us.dot.its.jpo.snmp.client.SnmpException.Kind.*;

/**
 * NTCIP 1218 device procedures built on the generic SNMP client.
 * Direct callers should share an instance for local write coordination. Task recipes use the
 * executor's endpoint serialization; separate executors or external writers need caller coordination.
 * Communication failures and malformed device data are reported as {@link SnmpException}.
 */
public final class Ntcip1218Client {
    private static final Logger log = LoggerFactory.getLogger(Ntcip1218Client.class);
    private static final String ROOT = "1.3.6.1.4.1.1206.4.2.18.";
    private final SnmpClient client;
    private final Clock clock;
    private final ReentrantLock[] locks = new ReentrantLock[256];

    /**
     * Borrows a generic client; this helper does not close it or own its transports.
     * @param client shared client for direct calls, or the current task's scoped client
     * @param clock clock used for UTC forwarding schedules
     */
    public Ntcip1218Client(SnmpClient client, Clock clock) {
        this.client = Objects.requireNonNull(client);
        this.clock = Objects.requireNonNull(clock);
        for (int i = 0; i < locks.length; i++) locks[i] = new ReentrantLock(true);
    }

    /**
     * Reads both tables and returns only a complete, validated snapshot.
     * @return immutable rows in RECEIVE-then-TRANSMIT order, with ascending indices in each table
     * @throws SnmpException if a read fails or any row is incomplete or malformed
     */
    public List<ForwardingRow> readForwarding(Ntcip1218Device device) {
        return exclusive(device, "FORWARDING_READ", false, trace -> {
            trace.step("read_receive_rows");
            List<ForwardingRow> rows = new ArrayList<>(readTable(device, ForwardingTable.RECEIVE));

            trace.step("read_transmit_rows");
            rows.addAll(readTable(device, ForwardingTable.TRANSMIT));

            return List.copyOf(rows);
        });
    }

    private List<ForwardingRow> readTable(Ntcip1218Device device, ForwardingTable table) {
        Map<Integer, Map<Integer, SnmpValue>> rows = new java.util.TreeMap<>();
        try {
            for (SnmpBinding binding : client.walk(device.target(), table.root())) {
                String prefix = table.root().value() + ".1.";
                if (!binding.oid().value().startsWith(prefix)) throw new IllegalArgumentException();
                String[] suffix = binding.oid().value().substring(prefix.length()).split("\\.");
                if (suffix.length != 2) throw new IllegalArgumentException();
                int column = Integer.parseInt(suffix[0]), index = Integer.parseInt(suffix[1]);
                table.cell(column, index);
                if (rows.computeIfAbsent(index, ignored -> new LinkedHashMap<>()).put(column, binding.value()) != null) throw new IllegalArgumentException();
            }
            return rows.entrySet().stream().map(e -> new ForwardingRow(table, e.getKey(), e.getValue())).toList();
        } catch (IllegalArgumentException e) {
            throw new SnmpException(INVALID_OUTPUT, false);
        }
    }

    /** Verify every column of one row without walking unrelated configurations again. */
    private Optional<ForwardingRow> readRow(Ntcip1218Device device, ForwardingTable table, int index) {
        var oids = java.util.stream.IntStream.rangeClosed(2, table.lastColumn())
                .mapToObj(column -> table.cell(column, index)).toList();
        var bindings = client.get(device.target(), oids);
        if (bindings.size() != oids.size()) throw new SnmpException(INVALID_OUTPUT, false);
        var columns = new LinkedHashMap<Integer, SnmpValue>();
        int missing = 0;
        for (int i = 0; i < bindings.size(); i++) {
            var binding = bindings.get(i);
            if (!binding.oid().equals(oids.get(i))) throw new SnmpException(INVALID_OUTPUT, false);
            if (binding.value() instanceof SnmpValue.ExceptionValue exception
                    && exception.kind() == SnmpValue.ExceptionValue.Kind.NO_SUCH_INSTANCE) missing++;
            columns.put(i + 2, binding.value());
        }
        // A deleted row must be absent in every requested column. Partial absence is malformed
        // output, not proof that a delete succeeded.
        if (missing == bindings.size()) return Optional.empty();

        try {
            return Optional.of(new ForwardingRow(table, index, columns));
        } catch (IllegalArgumentException failure) {
            throw new SnmpException(INVALID_OUTPUT, false);
        }
    }

    /**
     * Adds one row using the message type's table and port, then verifies every written column.
     * Row allocation uses max+1 up to 127 to preserve the previous API's behavior.
     * @return verified change and the created row; each call adds a row rather than updating one
     * @throws SnmpException if communication or verification fails; a successful SET followed by
     *         failed readback leaves the write outcome uncertain and must not be blindly replayed
     * @throws IllegalStateException if the next row exceeds the compatibility limit
     */
    public SnmpWriteResult<ForwardingRow> addForwarding(Ntcip1218Device device, ForwardingRequest request) {
        Objects.requireNonNull(request);
        return exclusive(device, "FORWARDING_ADD", true, trace -> {
            ForwardingTable table = request.messageType().table();
            trace.step("read_existing_rows");
            int index = readTable(device, table).stream().mapToInt(ForwardingRow::index).max().orElse(0) + 1;

            trace.step("select_row");
            if (index > 127) throw new IllegalStateException("Forwarding index exceeds compatibility limit 127");
            trace.rowSelected(index);
            List<SnmpBinding> expected = createBindings(request, index);

            trace.step("create_row");
            trace.writeStarting();
            client.set(device.target(), expected);

            try {
                trace.step("verify_row");
                ForwardingRow row = readRow(device, table, index)
                        .orElseThrow(() -> new SnmpException(VERIFICATION, true));
                for (SnmpBinding binding : expected) {
                    String[] parts = binding.oid().value().split("\\.");
                    int column = Integer.parseInt(parts[parts.length - 2]);
                    // createAndGo is a SET instruction; an active row reads back as status 1.
                    SnmpValue value = column == table.statusColumn() ? new SnmpValue.IntegerValue(1) : binding.value();
                    if (!value.equals(row.columns().get(column))) throw new SnmpException(VERIFICATION, true);
                }

                return new SnmpWriteResult<>(true, row);
            } catch (SnmpException failure) {
                throw failure.withWriteOutcomeUnknown(true);
            }
        });
    }

    /**
     * Deletes the highest row matching PSID and destination, preserving the previous API's selection.
     * No match is a successful no-op. A matching row is verified absent after the SET.
     * @return changed=true for verified deletion, or false when no matching row exists
     * @throws IllegalArgumentException if the destination is not a literal IPv4 address
     * @throws SnmpException if communication or verification fails; inspect write uncertainty
     *         before attempting another delete
     */
    public SnmpWriteResult<Void> deleteForwarding(Ntcip1218Device device, MessageType message, String destination) {
        Objects.requireNonNull(message);
        SnmpTarget.validateIpv4(destination);
        return exclusive(device, "FORWARDING_DELETE", true, trace -> {
            ForwardingTable table = message.table();
            trace.step("find_matching_row");
            Optional<ForwardingRow> match = readTable(device, table).stream()
                    .filter(r -> r.psid().equals(message.psid()) && r.destination().equals(destination))
                    .max(Comparator.comparingInt(ForwardingRow::index));

            if (match.isEmpty()) {
                trace.event("no_matching_row");
                return new SnmpWriteResult<>(false, null);
            }
            int index = match.get().index();
            trace.rowSelected(index);
            trace.step("delete_row");
            trace.writeStarting();
            client.set(device.target(), List.of(new SnmpBinding(table.cell(table.statusColumn(), index), new SnmpValue.IntegerValue(6))));

            try {
                trace.step("verify_absence");
                if (readRow(device, table, index).isPresent()) throw new SnmpException(VERIFICATION, true);

            } catch (SnmpException failure) {
                throw failure.withWriteOutcomeUnknown(true);
            }
            return new SnmpWriteResult<>(true, null);
        });
    }

    /**
     * Builds a createAndGo SET payload without contacting the device. Its UTC schedule starts
     * at the current minute and stops ten years later.
     * @return immutable bindings for one row; constructing these does not reserve the row index
     * @throws IllegalArgumentException if the row index is outside the MIB's 1-255 range
     */
    public List<SnmpBinding> createBindings(ForwardingRequest request, int index) {
        var table = request.messageType().table();
        Map<Integer, SnmpValue> values = new LinkedHashMap<>();
        values.put(2, request.messageType().psid());
        values.put(3, SnmpValue.Octets.text(request.destination()));
        values.put(4, new SnmpValue.IntegerValue(request.messageType().port()));
        values.put(5, new SnmpValue.IntegerValue(2));

        if (table == ForwardingTable.RECEIVE) {
            values.put(6, new SnmpValue.IntegerValue(-100));
            values.put(7, new SnmpValue.IntegerValue(1));
        }

        // Both forwarding MIBs require eight-octet UTC dates, with no offset field.
        // Preserve the existing minute precision, independently of device and clock zones.
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
        values.put(table.startColumn(), NtcipDateAndTime.encode(now));
        values.put(table.stopColumn(), NtcipDateAndTime.encode(now.plusYears(10)));
        if (table == ForwardingTable.RECEIVE) values.put(table.statusColumn(), new SnmpValue.IntegerValue(4));
        values.put(table.securityColumn(), new SnmpValue.IntegerValue(request.includeSecurity() ? 1 : 0));
        if (table == ForwardingTable.RECEIVE) values.put(12, new SnmpValue.IntegerValue(0));
        else values.put(table.statusColumn(), new SnmpValue.IntegerValue(4));

        return values.entrySet().stream().map(e -> new SnmpBinding(table.cell(e.getKey(), index), e.getValue())).toList();
    }

    /** Returns the firmware version as text; a missing or non-octet value is a read failure. */
    public String readFirmwareVersion(Ntcip1218Device device) { return octets(read(device, "13.2.0")).text(); }

    /** Returns the MIB's status value (1-5), rejecting missing or out-of-range values. */
    public int readStatus(Ntcip1218Device device) {
        int status = integer(read(device, "16.10.0"));
        if (status < 1 || status > 5) throw new SnmpException(INVALID_OUTPUT, false);
        return status;
    }

    /** Coordinates in degrees; an empty value represents the MIB's unknown-coordinate sentinel. */
    public record Location(Optional<Double> latitude, Optional<Double> longitude) {}

    /** Reads latitude and longitude together and preserves an unknown value for either coordinate. */
    public Location readLocation(Ntcip1218Device device) {
        var result = client.get(device.target(), List.of(new SnmpOid(ROOT + "6.6.0"), new SnmpOid(ROOT + "6.7.0")));
        int lat = integer(result.get(0).value()), lon = integer(result.get(1).value());
        if (lat < -900000000 || lat > 900000001 || lon < -1800000000 || lon > 1800000001) throw new SnmpException(INVALID_OUTPUT, false);
        return new Location(lat == 900000001 ? Optional.empty() : Optional.of(lat / 10_000_000.0),
                lon == 1800000001 ? Optional.empty() : Optional.of(lon / 10_000_000.0));
    }

    /**
     * Reads expiration hours keyed by certificate-table row index.
     * Values remain raw: 0 means expiring that day, 254 means at least 254 hours, and 255 means expired.
     * @return immutable row-index/hour map
     */
    public Map<Integer, Integer> readCertificateExpirationHours(Ntcip1218Device device) { return hours(device, "8.10.1.4"); }

    /**
     * Reads the configured renewal lead time in hours before certificate expiration, keyed by row.
     * Unlike expiration hours, these are plain values in 0-65535, not expiration sentinels.
     * @return immutable row-index/lead-time map
     */
    public Map<Integer, Integer> readCertificateRequestHours(Ntcip1218Device device) { return hours(device, "8.10.1.5"); }

    private Map<Integer, Integer> hours(Ntcip1218Device device, String suffix) {
        SnmpOid root = new SnmpOid(ROOT + suffix);
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (SnmpBinding binding : client.walk(device.target(), root)) {
            if (!binding.oid().isWithin(root)) throw new SnmpException(INVALID_OUTPUT, false);
            String index = binding.oid().value().substring(root.value().length() + 1);
            try {
                int row = Integer.parseInt(index), value = integer(binding.value());
                if (row < 1 || value < 0 || value > (suffix.endsWith(".4") ? 255 : 65535) || result.put(row, value) != null) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw new SnmpException(INVALID_OUTPUT, false); }
        }
        return Map.copyOf(result);
    }
    private SnmpValue read(Ntcip1218Device device, String suffix) { return client.get(device.target(), List.of(new SnmpOid(ROOT + suffix))).getFirst().value(); }
    private static int integer(SnmpValue value) {
        if (value instanceof SnmpValue.IntegerValue integer) return integer.value();
        throw new SnmpException(INVALID_OUTPUT, false);
    }
    private static SnmpValue.Octets octets(SnmpValue value) {
        if (value instanceof SnmpValue.Octets octets) return octets;
        throw new SnmpException(INVALID_OUTPUT, false);
    }
    // Striped locks bound memory use for direct callers. A hash collision may serialize unrelated
    // devices; it cannot let writes to the same endpoint overlap within this client instance.
    private <T> T exclusive(Ntcip1218Device device, String name, boolean write, Function<OperationLog, T> action) {
        var target = device.target();
        ReentrantLock lock = locks[Math.floorMod(Objects.hash(target.address(), target.port()), locks.length)];
        try (var trace = new OperationLog(log, target, name, write)) {
            trace.step("device_lock");
            try {
                try {
                    lock.lockInterruptibly();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); throw new SnmpException(CANCELLED, false);
                }
                try {
                    T result = action.apply(trace);
                    trace.succeeded();
                    return result;
                } finally { lock.unlock(); }
            } catch (SnmpException failure) {
                trace.failed(failure);
                throw failure;
            } catch (RuntimeException failure) {
                trace.failedLocally();
                throw failure;
            }
        }
    }
}
