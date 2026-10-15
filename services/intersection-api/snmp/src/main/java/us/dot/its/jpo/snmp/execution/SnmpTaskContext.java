package us.dot.its.jpo.snmp.execution;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import us.dot.its.jpo.snmp.client.*;

/** Thread-confined execution access. Use this client for every call, including NTCIP convenience procedures. */
public final class SnmpTaskContext {
    private final SnmpTaskExecutor.Control control;
    private final SnmpClient transport;
    private final SnmpClient client = new SnmpClient() {
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
    };

    SnmpTaskContext(SnmpTaskExecutor.Control control, SnmpClient transport) { this.control = control; this.transport = transport; }
    public SnmpClient client() { control.requireWorker(); return client; }
    public SnmpTarget target() { control.requireWorker(); return control.task.target(); }
    public Duration remainingTime() { return Duration.ofNanos(control.remainingNanos()); }
    public void checkActive() { control.remainingNanos(); }
    public <T> T value(String stepName, Class<T> type) {
        control.requireWorker();
        synchronized (control) {
            return control.steps.stream().filter(step -> step.name.equals(stepName) && step.status == SnmpStepResult.Status.SUCCEEDED)
                    .findFirst().map(step -> type.cast(step.value)).orElseThrow(() -> new IllegalArgumentException("No completed value for step"));
        }
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
        } catch (Throwable failure) {
            var safe = new SnmpException(SnmpException.Kind.EXECUTION, operation.equals("SET") && command.submissions > 0);
            control.commandFailed(command, safe);
            throw safe;
        }
    }
}
