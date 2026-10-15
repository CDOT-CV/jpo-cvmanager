package us.dot.its.jpo.snmp.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.dot.its.jpo.snmp.client.*;
import us.dot.its.jpo.snmp.internal.OperationLog;
import static us.dot.its.jpo.snmp.client.SnmpException.Kind.*;
import static us.dot.its.jpo.snmp.execution.SnmpTaskResult.Status.FAILED;
import static us.dot.its.jpo.snmp.execution.SnmpTaskResult.Status.QUEUED;
import static us.dot.its.jpo.snmp.execution.SnmpTaskResult.Status.QUEUE_EXPIRED;
import static us.dot.its.jpo.snmp.execution.SnmpTaskResult.Status.REJECTED;
import static us.dot.its.jpo.snmp.execution.SnmpTaskResult.Status.RUNNING;
import static us.dot.its.jpo.snmp.execution.SnmpTaskResult.Status.SUCCEEDED;
import static us.dot.its.jpo.snmp.execution.SnmpTaskResult.Status.TIMED_OUT;

/** Bounded, fair per-endpoint task execution. Owns its workers/timers, but borrows the supplied client. */
public final class SnmpTaskExecutor implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SnmpTaskExecutor.class);
    private final SnmpClient client;
    private final SnmpExecutorOptions options;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledThreadPoolExecutor timers = new ScheduledThreadPoolExecutor(1,
            Thread.ofPlatform().daemon().name("snmp-execution-timer-", 0).factory());
    private final List<Control> queue = new ArrayList<>();
    private final Map<String, Control> running = new HashMap<>();
    private final Set<String> activeTargets = new HashSet<>();
    private boolean closed;
    private long completed, rejected, expired, succeeded, failed, timedOut, cancelled;

    public SnmpTaskExecutor(SnmpClient client, SnmpExecutorOptions options) {
        this.client = Objects.requireNonNull(client);
        this.options = Objects.requireNonNull(options);
        if (options.maxConcurrentTasks() > client.concurrencyLimit()) throw new IllegalArgumentException("Task concurrency exceeds client/session capacity");
        // Fail before accepting work if an implementation cannot honor shared deadlines and accounting.
        client.scoped(new SnmpCallContext() {
            @Override public SnmpRequestPolicy policy() { return SnmpRequestPolicy.defaults(); }
            @Override public long remainingNanos() { return Long.MAX_VALUE; }
            @Override public void beforeRequest(boolean write) { throw new IllegalStateException("Capability probe cannot send"); }
        });
        timers.setRemoveOnCancelPolicy(true);
    }

    public SnmpTaskHandle submit(SnmpTask task) { return submit(task, SnmpTaskOptions.defaults()); }
    public synchronized SnmpTaskHandle submit(SnmpTask task, SnmpTaskOptions taskOptions) {
        var control = new Control(Objects.requireNonNull(task), Objects.requireNonNull(taskOptions));
        var handle = new SnmpTaskHandle(this, control);
        pump();
        if (closed) {
            reject(control);
        } else if (running.size() < options.maxConcurrentTasks() && !activeTargets.contains(control.endpoint)) {
            start(control);
        } else if (queue.size() >= options.maxQueuedTasks()) {
            reject(control);
        } else {
            queue.add(control);
            control.timer = timers.schedule(() -> expire(control), taskOptions.queueTimeout().toNanos(), TimeUnit.NANOSECONDS);
            log.trace("SNMP {} queued [task={} target={} waiting={}]", task.name(), control.id, control.endpoint, queue.size());
        }
        return handle;
    }

    private void reject(Control control) {
        rejected++;
        control.finish(REJECTED, new SnmpFailure(BUSY, false, null, null));
        logResult(control);
    }

    private synchronized void expire(Control control) {
        if (queue.remove(control)) {
            expired++;
            control.finish(QUEUE_EXPIRED, null);
            logResult(control);
            pump();
        }
    }

    private void pump() {
        if (closed) return;
        for (int i = 0; i < queue.size();) {
            var next = queue.get(i);
            if (System.nanoTime() - next.queuedNanos >= next.options.queueTimeout().toNanos()) {
                queue.remove(i); next.timer.cancel(false); expired++; next.finish(QUEUE_EXPIRED, null);
                logResult(next);
            } else if (running.size() < options.maxConcurrentTasks() && !activeTargets.contains(next.endpoint)) {
                queue.remove(i); start(next);
            } else i++;
        }
    }

    private void start(Control control) {
        synchronized (control) {
            if (control.timer != null) control.timer.cancel(false);
            control.status = RUNNING;
            control.startedAt = Instant.now();
            control.startedNanos = System.nanoTime();
            control.deadline = control.startedNanos + control.options.executionTimeout().toNanos();
        }
        activeTargets.add(control.endpoint);
        running.put(control.id, control);
        control.timer = timers.schedule(() -> {
            if (control.requestStop(TIMEOUT)) logResult(control);
        }, control.options.executionTimeout().toNanos(), TimeUnit.NANOSECONDS);
        workers.execute(() -> execute(control));
    }

    private void execute(Control control) {
        synchronized (control) { control.worker = Thread.currentThread(); }
        try (var trace = new OperationLog(log, control.task.target(), "TASK_" + control.task.name(), false, control.id)) {
            var context = new SnmpTaskContext(control, client);
            try {
                for (int i = 0; i < control.task.steps().size(); i++) {
                    control.remainingNanos();
                    var definition = control.task.steps().get(i);
                    var step = control.startStep(i);
                    trace.step(step.name);
                    try {
                        Object value = definition.action().execute(context);
                        if (definition.kind() == SnmpStep.Kind.VERIFY && !Boolean.TRUE.equals(value)) throw new SnmpException(VERIFICATION, false);
                        control.stepSucceeded(step, value);
                        trace.stepSucceeded();
                    } catch (Throwable failure) {
                        var safe = safeFailure(failure, control);
                        control.stepFailed(step, safe);
                        throw safe;
                    }
                }
                control.remainingNanos();
                control.finish(SUCCEEDED, null);
                if (control.snapshot().status() == SUCCEEDED) trace.succeeded();
                else trace.failed(new SnmpException(control.stop, false));
            } catch (SnmpException failure) {
                control.finish(failure.kind() == TIMEOUT ? TIMED_OUT : failure.kind() == CANCELLED ? SnmpTaskResult.Status.CANCELLED : FAILED, SnmpFailure.from(failure));
                trace.failed(failure);
            }
        } catch (Throwable failure) {
            var safe = safeFailure(failure, control);
            control.finish(FAILED, SnmpFailure.from(safe));
        } finally {
            control.timer.cancel(false);
            var result = control.snapshot();
            try {
                logResult(control);
            } finally {
                try {
                    synchronized (this) {
                        running.remove(control.id);
                        activeTargets.remove(control.endpoint);
                        completed++;
                        switch (result.status()) {
                            case SUCCEEDED -> succeeded++;
                            case TIMED_OUT -> timedOut++;
                            case CANCELLED -> cancelled++;
                            default -> failed++;
                        }
                        pump();
                    }
                } finally {
                    control.done.countDown();
                }
            }
        }
    }

    private void logResult(Control control) {
        var result = control.snapshot();
        var level = org.slf4j.event.Level.DEBUG;
        if (result.terminal()) {
            boolean incompleteWrite = result.writeState() == SnmpTaskResult.WriteState.ACKNOWLEDGED_UNVERIFIED && result.status() != SUCCEEDED;
            boolean routineFailure = result.failure() == null || switch (result.failure().kind()) {
                case TIMEOUT, CANCELLED, BUSY -> true;
                default -> false;
            };
            if (result.writeState() == SnmpTaskResult.WriteState.UNKNOWN || result.writeState() == SnmpTaskResult.WriteState.VERIFICATION_FAILED
                    || incompleteWrite || !routineFailure) level = org.slf4j.event.Level.WARN;
            else if (result.status() == SUCCEEDED && result.steps().stream().anyMatch(step -> step.kind() == SnmpStep.Kind.WRITE)) level = org.slf4j.event.Level.INFO;
        }
        log.atLevel(level).log("SNMP {} - {}", control.task.name(), result.summary());
    }

    private static SnmpException safeFailure(Throwable failure, Control control) {
        if (failure instanceof SnmpException known) return known;
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            synchronized (control) { return new SnmpException(control.stop == TIMEOUT ? TIMEOUT : CANCELLED, false); }
        }
        return new SnmpException(EXECUTION, false);
    }

    synchronized boolean cancel(Control control) {
        if (queue.remove(control)) {
            control.timer.cancel(false);
            control.requestStop(CANCELLED);
            control.finish(SnmpTaskResult.Status.CANCELLED, new SnmpFailure(CANCELLED, false, null, null));
            cancelled++;
            logResult(control);
            pump();
            return true;
        }
        return control.requestStop(CANCELLED);
    }

    public record Diagnostics(int queuedTasks, int runningTasks, int activeTargets, long completedTasks, long rejectedTasks,
            long expiredTasks, long succeededTasks, long failedTasks, long timedOutTasks, long cancelledTasks) {}
    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(queue.size(), running.size(), activeTargets.size(), completed, rejected, expired, succeeded, failed, timedOut, cancelled);
    }

    @Override public void close() {
        boolean ownWorker;
        synchronized (this) {
            if (closed) return;
            closed = true;
            for (var item : queue) {
                item.timer.cancel(false); item.requestStop(CANCELLED);
                item.finish(SnmpTaskResult.Status.CANCELLED, new SnmpFailure(CANCELLED, false, null, null));
                cancelled++;
                logResult(item);
            }
            queue.clear();
            ownWorker = running.values().stream().anyMatch(item -> item.worker == Thread.currentThread());
            running.values().forEach(item -> item.requestStop(CANCELLED));
            timers.shutdownNow();
            workers.shutdown();
        }
        if (!ownWorker) {
            try { workers.awaitTermination(options.shutdownTimeout().toNanos(), TimeUnit.NANOSECONDS); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
        }
    }

    static final class Control {
        final String id = UUID.randomUUID().toString();
        final SnmpTask task;
        final SnmpTaskOptions options;
        final String endpoint;
        final Instant submittedAt = Instant.now();
        final long queuedNanos = System.nanoTime();
        final CountDownLatch done = new CountDownLatch(1);
        final List<MutableStep> steps;
        SnmpTaskResult.Status status = QUEUED;
        SnmpException.Kind stop;
        Instant startedAt, finishedAt;
        long startedNanos, finishedNanos, deadline;
        int submissions, commandSequence;
        MutableStep current;
        Thread worker;
        ScheduledFuture<?> timer;
        SnmpFailure failure;
        Control(SnmpTask task, SnmpTaskOptions options) {
            this.task = task; this.options = options; endpoint = task.target().agent();
            steps = task.steps().stream().map(MutableStep::new).toList();
        }
        synchronized void requireWorker() {
            if (Thread.currentThread() != worker || status != RUNNING) throw new IllegalStateException("Task context is only valid on its running worker");
        }
        synchronized long remainingNanos() {
            requireWorker();
            if (stop == null && System.nanoTime() >= deadline) stop = TIMEOUT;
            if (stop == null && Thread.currentThread().isInterrupted()) stop = CANCELLED;
            if (stop != null) throw new SnmpException(stop, false);
            return Math.max(1, deadline - System.nanoTime());
        }
        synchronized boolean requestStop(SnmpException.Kind reason) {
            if (status != QUEUED && status != RUNNING || stop != null) return false;
            stop = reason;
            if (worker != null) worker.interrupt();
            return true;
        }
        synchronized MutableStep startStep(int index) {
            remainingNanos();
            current = steps.get(index); current.status = SnmpStepResult.Status.RUNNING; current.startedAt = Instant.now();
            return current;
        }
        synchronized void stepSucceeded(MutableStep step, Object value) {
            failIfCommandFailed();
            step.value = value instanceof List<?> list ? List.copyOf(list) : value;
            step.status = SnmpStepResult.Status.SUCCEEDED; step.finishedAt = Instant.now();
        }
        synchronized void stepFailed(MutableStep step, SnmpException exception) {
            step.failure = SnmpFailure.from(exception); step.finishedAt = Instant.now();
            boolean unconfirmedWrite = step.commands.stream().anyMatch(command -> command.status == SnmpCommandResult.Status.WRITE_OUTCOME_UNKNOWN);
            boolean readTimeout = step.commands.stream().anyMatch(command -> command.status == SnmpCommandResult.Status.READ_TIMED_OUT);
            step.status = unconfirmedWrite ? SnmpStepResult.Status.WRITE_OUTCOME_UNKNOWN
                    : exception.kind() == VERIFICATION ? SnmpStepResult.Status.VERIFICATION_FAILED
                    : readTimeout ? SnmpStepResult.Status.READ_TIMED_OUT
                    : exception.writeOutcomeUnknown() ? SnmpStepResult.Status.WRITE_OUTCOME_UNKNOWN
                    : exception.kind() == TIMEOUT ? SnmpStepResult.Status.TIMED_OUT
                    : exception.kind() == CANCELLED ? SnmpStepResult.Status.CANCELLED
                    : exception.kind() == AGENT_REJECTED ? SnmpStepResult.Status.REJECTED : SnmpStepResult.Status.FAILED;
        }
        synchronized MutableCommand startCommand(String operation, List<SnmpOid> oids, List<SnmpBinding> bindings) {
            requireWorker();
            if (current == null) throw new IllegalStateException("No executing step");
            failIfCommandFailed();
            if (commandSequence >= options.maxRecordedCommands()) {
                current.failure = new SnmpFailure(OUTPUT_LIMIT, false, null, null);
                throw new SnmpException(OUTPUT_LIMIT, false);
            }
            if (operation.equals("SET") && current.kind != SnmpStep.Kind.WRITE) throw new IllegalStateException("SET requires a WRITE step");
            var command = new MutableCommand(++commandSequence, operation, oids, bindings);
            current.commands.add(command);
            return command;
        }
        private void failIfCommandFailed() {
            if (current == null) return;
            if (current.failure != null) {
                var failure = current.failure;
                throw new SnmpException(failure.kind(), failure.writeOutcomeUnknown(), failure.agentErrorStatus(), failure.agentErrorIndex());
            }
            for (var command : current.commands) if (command.failure != null) {
                var failure = command.failure;
                throw new SnmpException(failure.kind(), failure.writeOutcomeUnknown(), failure.agentErrorStatus(), failure.agentErrorIndex());
            }
        }
        synchronized void beforeRequest(MutableCommand command, boolean write) {
            remainingNanos();
            if (options.maxRequests() > 0 && submissions >= options.maxRequests()) throw new SnmpException(REQUEST_LIMIT, false);
            if (write && current.kind != SnmpStep.Kind.WRITE) throw new IllegalStateException("SET requires a WRITE step");
            submissions++; command.submissions++;
        }
        synchronized void commandSucceeded(MutableCommand command, List<SnmpBinding> response) {
            command.response = response; command.status = SnmpCommandResult.Status.SUCCEEDED; command.finishedAt = Instant.now();
        }
        synchronized void commandFailed(MutableCommand command, SnmpException exception) {
            command.failure = SnmpFailure.from(exception); command.finishedAt = Instant.now();
            command.status = exception.writeOutcomeUnknown() ? SnmpCommandResult.Status.WRITE_OUTCOME_UNKNOWN
                    : command.submissions == 0 ? SnmpCommandResult.Status.NOT_DISPATCHED
                    : exception.kind() == TIMEOUT ? SnmpCommandResult.Status.READ_TIMED_OUT
                    : exception.kind() == CANCELLED ? SnmpCommandResult.Status.CANCELLED
                    : exception.kind() == AGENT_REJECTED ? SnmpCommandResult.Status.REJECTED : SnmpCommandResult.Status.FAILED;
        }
        synchronized void finish(SnmpTaskResult.Status result, SnmpFailure failure) {
            if (status != QUEUED && status != RUNNING) return;
            if (stop != null && status == RUNNING) result = stop == TIMEOUT ? TIMED_OUT : SnmpTaskResult.Status.CANCELLED;
            if (failure == null && stop != null) failure = new SnmpFailure(stop, false, null, null);
            this.failure = failure;
            status = result; finishedAt = Instant.now(); finishedNanos = System.nanoTime();
            if (startedAt == null) done.countDown();
        }
        synchronized SnmpTaskResult snapshot() {
            long now = finishedAt == null ? System.nanoTime() : finishedNanos;
            var results = steps.stream().map(MutableStep::snapshot).toList();
            return new SnmpTaskResult(id, task.name(), endpoint, status, writeState(results), submittedAt, startedAt, finishedAt,
                    Duration.ofNanos(Math.max(0, (startedAt == null ? now : startedNanos) - queuedNanos)),
                    Duration.ofNanos(startedAt == null ? 0 : Math.max(0, now - startedNanos)),
                    stop == CANCELLED, stop == TIMEOUT, submissions, results, failure);
        }
        private static SnmpTaskResult.WriteState writeState(List<SnmpStepResult> steps) {
            boolean rejected = false, acknowledged = false;
            int lastWrite = -1, lastVerification = -1;
            boolean verificationFailed = false;
            for (int i = 0; i < steps.size(); i++) {
                var step = steps.get(i);
                for (var command : step.commands()) if (command.operation().equals("SET") && command.submissions() > 0) {
                    if (command.status() == SnmpCommandResult.Status.WRITE_OUTCOME_UNKNOWN || command.status() == SnmpCommandResult.Status.RUNNING)
                        return SnmpTaskResult.WriteState.UNKNOWN;
                    if (command.status() == SnmpCommandResult.Status.SUCCEEDED) { acknowledged = true; lastWrite = i; }
                    if (command.status() == SnmpCommandResult.Status.REJECTED) rejected = true;
                }
                if (step.kind() == SnmpStep.Kind.VERIFY && step.status() == SnmpStepResult.Status.SUCCEEDED) lastVerification = i;
                if (step.status() == SnmpStepResult.Status.VERIFICATION_FAILED) verificationFailed = true;
            }
            if (acknowledged) return verificationFailed ? SnmpTaskResult.WriteState.VERIFICATION_FAILED
                    : lastVerification > lastWrite ? SnmpTaskResult.WriteState.VERIFIED : SnmpTaskResult.WriteState.ACKNOWLEDGED_UNVERIFIED;
            return rejected ? SnmpTaskResult.WriteState.REJECTED : SnmpTaskResult.WriteState.NOT_SUBMITTED;
        }
    }

    static final class MutableStep {
        final String name; final SnmpStep.Kind kind;
        SnmpStepResult.Status status = SnmpStepResult.Status.NOT_STARTED;
        Instant startedAt, finishedAt;
        Object value; SnmpFailure failure;
        final List<MutableCommand> commands = new ArrayList<>();
        MutableStep(SnmpStep definition) { name = definition.name(); kind = definition.kind(); }
        SnmpStepResult snapshot() { return new SnmpStepResult(name, kind, status, startedAt, finishedAt, value, commands.stream().map(MutableCommand::snapshot).toList(), failure); }
    }
    static final class MutableCommand {
        final int sequence; final String operation;
        final List<SnmpOid> oids; final List<SnmpBinding> bindings;
        final Instant startedAt = Instant.now();
        Instant finishedAt; int submissions;
        SnmpCommandResult.Status status = SnmpCommandResult.Status.RUNNING;
        List<SnmpBinding> response = List.of(); SnmpFailure failure;
        MutableCommand(int sequence, String operation, List<SnmpOid> oids, List<SnmpBinding> bindings) {
            this.sequence = sequence; this.operation = operation; this.oids = oids; this.bindings = bindings;
        }
        SnmpCommandResult snapshot() { return new SnmpCommandResult(sequence, operation, status, startedAt, finishedAt, submissions, oids, bindings, response, failure); }
    }
}
