package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import us.dot.its.jpo.snmp.client.SnmpTarget;
import us.dot.its.jpo.snmp.client.SnmpWriteResult;
import us.dot.its.jpo.snmp.execution.*;

/**
 * Reusable factory for forwarding tasks; building a task performs no device I/O.
 * Each procedure reads the affected table to find a row, then verifies the changed row.
 * Snapshot variants also read both tables and may pass the validated snapshot to application code.
 * A snapshot or callback failure can fail the task without erasing an already verified change.
 */
public final class Ntcip1218ForwardingTasks {
    private final Clock clock;

    /** Creates a reusable factory using the supplied clock for UTC forwarding schedules. */
    public Ntcip1218ForwardingTasks(Clock clock) { this.clock = Objects.requireNonNull(clock); }

    /** Adds one forwarding row and verifies it, without reading an unrelated table afterward. */
    public SnmpTask create(Ntcip1218Device device, ForwardingRequest request) {
        return createTask(device, request, false, null);
    }

    /** Builds a create task followed by a complete snapshot in its read-current-forwarding step. */
    public SnmpTask createWithSnapshot(Ntcip1218Device device, ForwardingRequest request) {
        return createTask(device, request, true, null);
    }

    /**
     * Builds a create task, snapshot read and local callback, all within the same task deadline.
     * The callback runs only after successful verification and a complete snapshot.
     * @param afterRead bounded application work on the validated snapshot
     */
    public SnmpTask createWithSnapshot(Ntcip1218Device device, ForwardingRequest request, SnapshotAction afterRead) {
        return createTask(device, request, true, Objects.requireNonNull(afterRead));
    }

    private SnmpTask createTask(Ntcip1218Device device, ForwardingRequest request, boolean snapshot, SnapshotAction afterRead) {
        Objects.requireNonNull(request);
        return task("create-message-forwarding", device, request.messageType(),
                client -> client.addForwarding(device, request), snapshot, afterRead);
    }

    /**
     * Deletes the highest matching row, or reports a successful no-op when none exists.
     * @throws IllegalArgumentException if the destination is not a literal IPv4 address
     */
    public SnmpTask delete(Ntcip1218Device device, MessageType message, String destination) {
        return deleteTask(device, message, destination, false, null);
    }

    /**
     * Builds a delete/no-op task followed by a complete snapshot in its read-current-forwarding step.
     * @throws IllegalArgumentException if the destination is not a literal IPv4 address
     */
    public SnmpTask deleteWithSnapshot(Ntcip1218Device device, MessageType message, String destination) {
        return deleteTask(device, message, destination, true, null);
    }

    /**
     * Builds a delete/no-op task, snapshot read and local callback within the same task deadline.
     * The callback is skipped if the procedure or snapshot fails.
     * @param afterRead bounded application work on the validated snapshot
     * @throws IllegalArgumentException if the destination is not a literal IPv4 address
     */
    public SnmpTask deleteWithSnapshot(Ntcip1218Device device, MessageType message, String destination, SnapshotAction afterRead) {
        return deleteTask(device, message, destination, true, Objects.requireNonNull(afterRead));
    }

    private SnmpTask deleteTask(Ntcip1218Device device, MessageType message, String destination, boolean snapshot, SnapshotAction afterRead) {
        Objects.requireNonNull(message);
        SnmpTarget.validateIpv4(destination);
        return task("delete-message-forwarding", device, message,
                client -> client.deleteForwarding(device, message, destination), snapshot, afterRead);
    }

    /**
     * Reads procedure evidence from a result produced by this factory. A later snapshot or callback
     * failure does not erase this evidence.
     * @return true for a verified change, false for a confirmed no-op, or null if the procedure did not finish
     */
    public static Boolean changed(SnmpTaskResult result) {
        return result.steps().stream().filter(step -> step.kind() == SnmpStep.Kind.WRITE
                && step.status() == SnmpStepResult.Status.SUCCEEDED && step.value() instanceof SnmpWriteResult<?>)
                .map(step -> ((SnmpWriteResult<?>) step.value()).changed()).findFirst().orElse(null);
    }

    private <T> SnmpTask task(String name, Ntcip1218Device device, MessageType message,
            Function<Ntcip1218Client, SnmpWriteResult<T>> change, boolean snapshot, SnapshotAction afterRead) {
        Objects.requireNonNull(device);
        // Each execution uses its task-scoped client so helper calls share deadlines and tracking.
        var configure = new SnmpStep<>("configure-forwarding", SnmpStep.Kind.WRITE,
                ctx -> change.apply(new Ntcip1218Client(ctx.client(), clock)));
        var steps = new ArrayList<SnmpStep<?>>();
        steps.add(configure);

        if (snapshot) {
            var read = new SnmpStep<>("read-current-forwarding", SnmpStep.Kind.READ,
                    ctx -> new Ntcip1218Client(ctx.client(), clock).readForwarding(device));
            steps.add(read);
            // Keeping application work in this task holds the endpoint slot through persistence.
            if (afterRead != null) steps.add(new SnmpStep<>(afterRead.stepName(), SnmpStep.Kind.LOCAL, ctx -> {
                afterRead.action().accept(ctx.value(read));
                return null;
            }));
        }

        return new SnmpTask(name + "." + message.name() + "." + message.table().name(), device.target(), steps);
    }

    /**
     * Application work on a complete immutable snapshot, within the device task's deadline.
     * The callback runs on the task worker; keep it bounded and responsive to interruption.
     *
     * @param stepName unique task-step label, validated when the task is built
     * @param action callback invoked while this task still holds its endpoint slot
     */
    public record SnapshotAction(String stepName, Consumer<List<ForwardingRow>> action) {
        public SnapshotAction { Objects.requireNonNull(stepName); Objects.requireNonNull(action); }
    }
}
