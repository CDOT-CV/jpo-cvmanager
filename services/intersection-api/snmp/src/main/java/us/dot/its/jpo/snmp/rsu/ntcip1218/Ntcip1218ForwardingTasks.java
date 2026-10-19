package us.dot.its.jpo.snmp.rsu.ntcip1218;

import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import us.dot.its.jpo.snmp.client.SnmpTarget;
import us.dot.its.jpo.snmp.execution.SnmpStep;
import us.dot.its.jpo.snmp.execution.SnmpStepResult;
import us.dot.its.jpo.snmp.execution.SnmpTask;
import us.dot.its.jpo.snmp.execution.SnmpTaskContext;
import us.dot.its.jpo.snmp.execution.SnmpTaskResult;

/**
 * Reusable forwarding task recipes: verified change, complete snapshot, then optional caller work.
 *
 * Owns no transport, queue or task state. Every SNMP operation uses the executor's scoped client,
 * preserving request policies, deadlines and command history when tasks run concurrently.
 * Other features compose their own SnmpTask recipes; this class is not an executor extension point.
 */
public final class Ntcip1218ForwardingTasks {
    private static final String CONFIGURE = "configure-forwarding";
    private static final String SNAPSHOT = "read-current-forwarding";
    private final Clock clock;
    private final ZoneId deviceZone;

    public Ntcip1218ForwardingTasks(Clock clock, ZoneId deviceZone) {
        this.clock = Objects.requireNonNull(clock);
        this.deviceZone = Objects.requireNonNull(deviceZone);
    }

    public SnmpTask create(Ntcip1218Device device, ForwardingRequest request) {
        return create(device, request, null);
    }

    /** Allocates a new row; the optional action runs only after a complete two-table snapshot. */
    public SnmpTask create(Ntcip1218Device device, ForwardingRequest request, SnapshotAction afterRead) {
        Objects.requireNonNull(request);
        return task("create-message-forwarding", device, request.messageType(), client -> {
            client.addForwarding(device, request);
            return true;
        }, afterRead);
    }

    public SnmpTask delete(Ntcip1218Device device, MessageType message, String destination) {
        return delete(device, message, destination, null);
    }

    /** Removes the highest matching row; confirmed absence is a successful, unchanged outcome. */
    public SnmpTask delete(Ntcip1218Device device, MessageType message, String destination, SnapshotAction afterRead) {
        Objects.requireNonNull(message);
        SnmpTarget.validateIpv4(destination);
        return task("delete-message-forwarding", device, message,
                client -> client.deleteForwarding(device, message, destination), afterRead);
    }

    /** True/false is confirmed procedure evidence; null means the procedure did not finish. */
    public static Boolean changed(SnmpTaskResult result) {
        return result.steps().stream().filter(step -> step.name().equals(CONFIGURE)
                && step.status() == SnmpStepResult.Status.SUCCEEDED)
                .map(step -> step.valueAs(Boolean.class)).findFirst().orElse(null);
    }

    private SnmpTask task(String name, Ntcip1218Device device, MessageType message,
            Function<Ntcip1218Client, Boolean> change, SnapshotAction afterRead) {
        Objects.requireNonNull(device);
        var steps = new ArrayList<SnmpStep>();
        steps.add(new SnmpStep(CONFIGURE, SnmpStep.Kind.WRITE, ctx -> change.apply(client(ctx))));
        // The procedure itself performs device readback before returning. False means delete no-op,
        // not verification failure. Do not infer this guarantee from an arbitrary successful GET.
        steps.add(new SnmpStep("confirm-result", SnmpStep.Kind.VERIFY,
                ctx -> ctx.value(CONFIGURE, Boolean.class) != null));
        steps.add(new SnmpStep(SNAPSHOT, SnmpStep.Kind.READ, ctx -> client(ctx).readForwarding(device)));
        if (afterRead != null) {
            steps.add(new SnmpStep(afterRead.stepName(), SnmpStep.Kind.LOCAL, ctx -> {
                List<?> snapshot = ctx.value(SNAPSHOT, List.class);
                afterRead.action().accept(snapshot.stream().map(ForwardingRow.class::cast).toList());
                return true;
            }));
        }
        // Keep CV context in the recipe's name; the generic executor need not know RSU MIBs.
        return new SnmpTask(name + "." + message.name() + "." + message.table().name(), device.target(), steps);
    }

    private Ntcip1218Client client(SnmpTaskContext context) {
        // This procedure wrapper opens no sockets or threads. Keeping it local to a step avoids
        // retaining a worker's scoped client or introducing shared procedure-lock contention.
        return new Ntcip1218Client(context.client(), clock, deviceZone);
    }

    /**
     * Named application work on an immutable complete snapshot, such as database synchronization.
     * Runs within the device's task slot and deadline; the action must cooperate with interruption.
     * Failures remain separate from the already verified device change in execution results.
     */
    public record SnapshotAction(String stepName, Consumer<List<ForwardingRow>> action) {
        public SnapshotAction {
            Objects.requireNonNull(stepName);
            Objects.requireNonNull(action);
        }
    }
}
