package us.dot.its.jpo.snmp.execution;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import us.dot.its.jpo.snmp.client.*;

/**
 * Thread-confined access and call history. Use this client even inside device procedures.
 * This wrapper records one logical call; the adapter's scoped client also checks each
 * underlying submission, because a single WALK may send several requests.
 */
public final class SnmpTaskContext {
    private final SnmpTaskExecutor.Control control;
    private final SnmpClient transport;
    private final SnmpClient client = new TrackedClient();

    SnmpTaskContext(SnmpTaskExecutor.Control control, SnmpClient transport) { this.control = control; this.transport = transport; }

    /**
     * Borrows the tracked client sharing this execution's target, deadline and call history.
     * @return client for use only on the running task worker
     * @throws IllegalStateException if accessed outside the running worker
     */
    public SnmpClient client() { control.requireWorker(); return client; }

    /**
     * Returns the endpoint and credentials assigned to this task.
     * @return task target
     * @throws IllegalStateException if accessed outside the running worker
     */
    public SnmpTarget target() { control.requireWorker(); return control.task.target(); }

    /**
     * Returns the shared budget after time spent in earlier steps.
     * @return remaining execution time
     * @throws us.dot.its.jpo.snmp.client.SnmpException if timed out or cancelled
     */
    public Duration remainingTime() { return Duration.ofNanos(control.remainingNanos()); }

    /**
     * Checks cancellation/deadline before expensive local work or the next procedure step.
     * @throws us.dot.its.jpo.snmp.client.SnmpException if timed out or cancelled
     */
    public void checkActive() { control.remainingNanos(); }

    /**
     * Reads an earlier successful step using its exact typed reference.
     * @param <T> step result type
     * @param step original step definition
     * @return successful action value, possibly null
     * @throws IllegalArgumentException if the definition does not belong to this task
     * @throws IllegalStateException if the step has not succeeded or this is not the running worker
     */
    @SuppressWarnings("unchecked")
    public <T> T value(SnmpStep<T> step) {
        control.requireWorker();
        synchronized (control) {
            for (var stored : control.steps) if (stored.definition == step) {
                if (stored.status != SnmpStepResult.Status.SUCCEEDED) throw new IllegalStateException("Step has not succeeded");
                // Only this exact typed definition can have produced the stored value.
                return (T) stored.value;
            }
            throw new IllegalArgumentException("Step does not belong to this task");
        }
    }

    /** Records logical calls and prevents helpers from bypassing the task's target and request policy. */
    private final class TrackedClient implements SnmpClient {
        @Override public List<SnmpBinding> get(SnmpTarget target, List<SnmpOid> oids) {
            var copy = List.copyOf(oids);
            return call(target, "GET", copy, List.of(), wire -> wire.get(target, copy));
        }
        @Override public List<SnmpBinding> getNext(SnmpTarget target, List<SnmpOid> oids) {
            var copy = List.copyOf(oids);
            return call(target, "GETNEXT", copy, List.of(), wire -> wire.getNext(target, copy));
        }
        @Override public List<SnmpBinding> walk(SnmpTarget target, SnmpOid subtree) {
            return call(target, "WALK", List.of(subtree), List.of(), wire -> wire.walk(target, subtree));
        }
        @Override public List<SnmpBinding> set(SnmpTarget target, List<SnmpBinding> bindings) {
            var copy = List.copyOf(bindings);
            return call(target, "SET", copy.stream().map(SnmpBinding::oid).toList(), copy, wire -> wire.set(target, copy));
        }

        private List<SnmpBinding> call(SnmpTarget target, String operation, List<SnmpOid> oids, List<SnmpBinding> bindings,
                Function<SnmpClient, List<SnmpBinding>> action) {
            control.requireWorker();
            if (!control.task.target().equals(target)) throw new IllegalArgumentException("Task calls must use its supplied target and credentials");
            var command = control.startCommand(operation, oids, bindings);
            SnmpCallContext scope = new SnmpCallContext() {
                @Override public SnmpRequestPolicy policy() { return control.options.requestPolicy(); }
                @Override public long remainingNanos() { return control.remainingNanos(); }
                @Override public void beforeRequest(boolean write) { control.beforeRequest(command, write); }
            };
            try {
                control.remainingNanos();
                var result = List.copyOf(action.apply(transport.scoped(scope)));
                control.commandSucceeded(command, result);
                return result;
            } catch (SnmpException failure) {
                control.commandFailed(command, failure);
                throw failure;
            } catch (Exception failure) {
                var safe = new SnmpException(SnmpException.Kind.EXECUTION, operation.equals("SET") && command.submissions > 0);
                control.commandFailed(command, safe);
                throw safe;
            } finally {
                // Preserve uncertain-write evidence if an Error escapes the transport.
                control.failUnfinishedCommand(command);
            }
        }
    }
}
