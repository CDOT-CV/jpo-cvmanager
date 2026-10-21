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

/**
 * Queues finite tasks and runs independent endpoints in parallel within configured limits.
 * One IP/port runs at a time, even when submitted with different credentials.
 * Owns workers and timers; the application still owns the supplied client.
 */
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
    private long completed;
    private long rejected;
    private long expired;
    private long succeeded;
    private long failed;
    private long timedOut;
    private long cancelled;

    /**
     * Creates an executor that borrows the supplied client and owns its workers/timers.
     * @param client scoped-execution-capable client, closed separately by its owner
     * @param options queue, concurrency and shutdown limits
     * @throws IllegalArgumentException if task concurrency exceeds client capacity
     * @throws UnsupportedOperationException if the client cannot honor scoped execution
     */
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

    /** Submits work using the default queue, execution and request policies. */
    public SnmpTaskHandle submit(SnmpTask task) { return submit(task, SnmpTaskOptions.defaults()); }

    /**
     * Returns a handle for accepted or rejected work. Capacity rejection is a task result,
     * so a batch can account for every device without losing rejected submissions.
     * @param task reusable definition; each submission has independent execution state
     * @param taskOptions policies for this execution
     * @return handle containing accepted, queued or rejected progress
     */
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
        // Skip a busy endpoint instead of occupying a worker while waiting for its lock.
        // The oldest eligible task wins; unrelated devices can still make progress.
        var pending = queue.iterator();
        while (pending.hasNext()) {
            var next = pending.next();
            if (System.nanoTime() - next.queuedNanos >= next.options.queueTimeout().toNanos()) {
                pending.remove();
                next.timer.cancel(false);
                expired++;
                next.finish(QUEUE_EXPIRED, null);
                logResult(next);
            } else if (running.size() < options.maxConcurrentTasks() && !activeTargets.contains(next.endpoint)) {
                pending.remove();
                start(next);
            }
        }
    }

    private void start(Control control) {
        control.start();
        activeTargets.add(control.endpoint);
        running.put(control.id, control);
        control.timer = timers.schedule(() -> {
            if (control.requestStop(TIMEOUT)) logResult(control);
        }, control.options.executionTimeout().toNanos(), TimeUnit.NANOSECONDS);
        workers.execute(() -> execute(control));
    }

    private void execute(Control control) {
        control.workerStarted();
        try (var trace = new OperationLog(log, control.task.target(), "TASK_" + control.task.name(), false, control.id)) {
            var context = new SnmpTaskContext(control, client);
            for (var step : control.steps) {
                control.startStep(step);
                executeStep(control, step, context, trace);
            }
            control.remainingNanos();
            control.finish(SUCCEEDED, null);
        } catch (Exception failure) {
            var safe = safeFailure(failure, control);
            control.finish(failureStatus(safe), SnmpFailure.from(safe));
        } finally {
            // Errors must propagate, but never leave a handle waiting or an endpoint occupied.
            control.finish(FAILED, SnmpFailure.from(new SnmpException(EXECUTION, false)));
            complete(control);
        }
    }

    private static SnmpTaskResult.Status failureStatus(SnmpException failure) {
        return switch (failure.kind()) {
            case TIMEOUT -> TIMED_OUT;
            case CANCELLED -> SnmpTaskResult.Status.CANCELLED;
            default -> FAILED;
        };
    }

    private void complete(Control control) {
        control.timer.cancel(false);
        try {
            logResult(control);
        } finally {
            release(control);
        }
    }

    private void release(Control control) {
        try {
            synchronized (this) {
                running.remove(control.id);
                activeTargets.remove(control.endpoint);
                completed++;
                switch (control.snapshot().status()) {
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

    private <T> void executeStep(Control control, MutableStep<T> step, SnmpTaskContext context, OperationLog trace) {
        var definition = step.definition;
        trace.step(step.definition.name());
        try {
            T value = definition.action().execute(context);
            if (definition.kind() == SnmpStep.Kind.VERIFY && !Boolean.TRUE.equals(value)) throw new SnmpException(VERIFICATION, false);
            control.stepSucceeded(step, value);

        } catch (Exception failure) {
            var safe = safeFailure(failure, control);
            control.stepFailed(step, safe);
            throw safe;
        } finally {
            control.failUnfinishedStep(step);
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
        log.atLevel(level).log("SNMP {} - {} [queue={}ms run={}ms requests={} steps={}]",
                control.task.name(), result.summary(), result.queueWait().toMillis(), result.executionTime().toMillis(),
                result.submissions(), stepTimings(result));
    }

    private static String stepTimings(SnmpTaskResult result) {
        var now = Instant.now();
        return result.steps().stream().filter(step -> step.startedAt() != null)
                .map(step -> step.name() + "=" + Math.max(0, Duration.between(step.startedAt(),
                        step.finishedAt() == null ? now : step.finishedAt()).toMillis()) + "ms")
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static SnmpException safeFailure(Exception failure, Control control) {
        if (failure instanceof SnmpException known) return known;
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return control.interruptedFailure();
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

    /**
     * Executor-local counts. A stopped callback remains running until it actually returns;
     * completedTasks counts workers that exited; cancelledTasks also includes queued cancellations.
     */
    public record Diagnostics(int queuedTasks, int runningTasks, int activeTargets, long completedTasks, long rejectedTasks,
            long expiredTasks, long succeededTasks, long failedTasks, long timedOutTasks, long cancelledTasks) {}

    /**
     * Returns current queue, worker and completion counts.
     * @return executor-local progress counters
     */
    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(queue.size(), running.size(), activeTargets.size(), completed, rejected, expired, succeeded, failed, timedOut, cancelled);
    }

    /**
     * Stops admission, cancels queued work and requests interruption of running tasks.
     * Waits at most the shutdown limit when called outside its own worker. Uncooperative callbacks
     * may outlive close; their handles retain progress. The borrowed SNMP client stays open.
     */
    @Override public void close() {
        boolean ownWorker;
        synchronized (this) {
            if (closed) return;
            closed = true;
            for (var item : queue) {
                item.timer.cancel(false);
                item.requestStop(CANCELLED);
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

    /**
     * Per-execution state transitions use this object's monitor. Callback code runs outside it,
     * allowing cancellation and deadline signals to be recorded while application work is running.
     */
    static final class Control {
        final String id = UUID.randomUUID().toString();
        final SnmpTask task;
        final SnmpTaskOptions options;
        final String endpoint;
        final Instant submittedAt = Instant.now();
        final long queuedNanos = System.nanoTime();
        final CountDownLatch done = new CountDownLatch(1);
        final List<MutableStep<?>> steps;
        SnmpTaskResult.Status status = QUEUED;
        SnmpException.Kind stop;
        Instant startedAt;
        Instant finishedAt;
        long startedNanos;
        long finishedNanos;
        long deadline;
        int submissions;
        int commandSequence;
        MutableStep<?> current;
        Thread worker;
        ScheduledFuture<?> timer;
        SnmpFailure failure;

        Control(SnmpTask task, SnmpTaskOptions options) {
            this.task = task;
            this.options = options;
            endpoint = task.target().agent();
            steps = new ArrayList<>();
            for (var definition : task.steps()) steps.add(new MutableStep<>(definition));
        }

        synchronized void start() {
            if (timer != null) timer.cancel(false);
            status = RUNNING;
            startedAt = Instant.now();
            startedNanos = System.nanoTime();
            deadline = startedNanos + options.executionTimeout().toNanos();
        }

        synchronized void workerStarted() { worker = Thread.currentThread(); }
        synchronized SnmpException interruptedFailure() {
            return new SnmpException(stop == TIMEOUT ? TIMEOUT : CANCELLED, false);
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

        synchronized void startStep(MutableStep<?> step) {
            remainingNanos();
            current = step;
            current.status = SnmpStepResult.Status.RUNNING;
            current.startedAt = Instant.now();
        }

        synchronized void failUnfinishedStep(MutableStep<?> step) {
            if (step.status == SnmpStepResult.Status.RUNNING) stepFailed(step, new SnmpException(EXECUTION, false));
        }

        synchronized <T> void stepSucceeded(MutableStep<T> step, T value) {
            failIfCommandFailed();
            step.value = value;
            step.status = SnmpStepResult.Status.SUCCEEDED;
            step.finishedAt = Instant.now();
        }

        synchronized void stepFailed(MutableStep<?> step, SnmpException exception) {
            step.failure = SnmpFailure.from(exception);
            step.finishedAt = Instant.now();
            step.status = stepFailureStatus(step, exception);
        }

        private static SnmpStepResult.Status stepFailureStatus(MutableStep<?> step, SnmpException exception) {
            if (step.commands.stream().anyMatch(command -> command.status == SnmpCommandResult.Status.WRITE_OUTCOME_UNKNOWN))
                return SnmpStepResult.Status.WRITE_OUTCOME_UNKNOWN;
            if (exception.kind() == VERIFICATION) return SnmpStepResult.Status.VERIFICATION_FAILED;
            if (step.commands.stream().anyMatch(command -> command.status == SnmpCommandResult.Status.READ_TIMED_OUT))
                return SnmpStepResult.Status.READ_TIMED_OUT;
            if (exception.writeOutcomeUnknown()) return SnmpStepResult.Status.WRITE_OUTCOME_UNKNOWN;
            return switch (exception.kind()) {
                case TIMEOUT -> SnmpStepResult.Status.TIMED_OUT;
                case CANCELLED -> SnmpStepResult.Status.CANCELLED;
                case AGENT_REJECTED -> SnmpStepResult.Status.REJECTED;
                default -> SnmpStepResult.Status.FAILED;
            };
        }

        synchronized MutableCommand startCommand(String operation, List<SnmpOid> oids, List<SnmpBinding> bindings) {
            requireWorker();
            if (current == null) throw new IllegalStateException("No executing step");
            failIfCommandFailed();
            if (commandSequence >= options.maxRecordedCommands()) {
                current.failure = new SnmpFailure(OUTPUT_LIMIT, false, null, null);
                throw new SnmpException(OUTPUT_LIMIT, false);
            }
            if (operation.equals("SET") && current.definition.kind() != SnmpStep.Kind.WRITE) throw new IllegalStateException("SET requires a WRITE step");
            var command = new MutableCommand(++commandSequence, operation, oids, bindings);
            current.commands.add(command);
            return command;
        }

        private void failIfCommandFailed() {
            if (current == null) return;
            if (current.failure != null) {
                var commandFailure = current.failure;
                throw new SnmpException(commandFailure.kind(), commandFailure.writeOutcomeUnknown(), commandFailure.agentErrorStatus(), commandFailure.agentErrorIndex());
            }
        }

        synchronized void beforeRequest(MutableCommand command, boolean write) {
            remainingNanos();
            if (options.maxRequests() > 0 && submissions >= options.maxRequests()) throw new SnmpException(REQUEST_LIMIT, false);
            if (write && current.definition.kind() != SnmpStep.Kind.WRITE) throw new IllegalStateException("SET requires a WRITE step");
            submissions++;
            command.submissions++;
        }

        synchronized void commandSucceeded(MutableCommand command, List<SnmpBinding> response) {
            command.response = response;
            command.status = SnmpCommandResult.Status.SUCCEEDED;
            command.finishedAt = Instant.now();
        }

        synchronized void commandFailed(MutableCommand command, SnmpException exception) {
            command.failure = SnmpFailure.from(exception);
            command.finishedAt = Instant.now();
            // Keep the first failed call sticky even if an action catches its exception.
            // Later admission/success checks need not rescan the growing command history.
            if (current.failure == null) current.failure = command.failure;
            command.status = commandFailureStatus(command, exception);
        }

        private static SnmpCommandResult.Status commandFailureStatus(MutableCommand command, SnmpException exception) {
            if (exception.writeOutcomeUnknown()) return SnmpCommandResult.Status.WRITE_OUTCOME_UNKNOWN;
            if (command.submissions == 0) return SnmpCommandResult.Status.NOT_DISPATCHED;
            return switch (exception.kind()) {
                case TIMEOUT -> SnmpCommandResult.Status.READ_TIMED_OUT;
                case CANCELLED -> SnmpCommandResult.Status.CANCELLED;
                case AGENT_REJECTED -> SnmpCommandResult.Status.REJECTED;
                default -> SnmpCommandResult.Status.FAILED;
            };
        }

        synchronized void failUnfinishedCommand(MutableCommand command) {
            if (command.status == SnmpCommandResult.Status.RUNNING) {
                boolean uncertain = command.operation.equals("SET") && command.submissions > 0;
                commandFailed(command, new SnmpException(EXECUTION, uncertain));
            }
        }

        synchronized void finish(SnmpTaskResult.Status result, SnmpFailure failure) {
            // Stop requests do not finish callbacks. Only the worker acknowledges completion;
            // releasing the device slot sooner would allow overlapping work on that device.
            if (status != QUEUED && status != RUNNING) return;
            if (stop != null && status == RUNNING) result = stop == TIMEOUT ? TIMED_OUT : SnmpTaskResult.Status.CANCELLED;
            if (failure == null && stop != null) failure = new SnmpFailure(stop, false, null, null);
            this.failure = failure;
            status = result;
            finishedAt = Instant.now();
            finishedNanos = System.nanoTime();
            if (startedAt == null) done.countDown();
        }

        synchronized SnmpTaskResult snapshot() {
            long now = finishedAt == null ? System.nanoTime() : finishedNanos;
            var results = steps.stream().<SnmpStepResult<?>>map(MutableStep::snapshot).toList();
            return new SnmpTaskResult(id, task.name(), endpoint, status, writeState(results), submittedAt, startedAt, finishedAt,
                    Duration.ofNanos(Math.max(0, (startedAt == null ? now : startedNanos) - queuedNanos)),
                    Duration.ofNanos(startedAt == null ? 0 : Math.max(0, now - startedNanos)),
                    stop == CANCELLED, stop == TIMEOUT, submissions, results, failure);
        }

        /** Derives write evidence from recorded calls, independently of later step failures. */
        private static SnmpTaskResult.WriteState writeState(List<SnmpStepResult<?>> steps) {
            // A pending or unconfirmed SET takes precedence over any earlier verified change.
            if (steps.stream().flatMap(step -> step.commands().stream()).anyMatch(Control::uncertainWrite))
                return SnmpTaskResult.WriteState.UNKNOWN;
            boolean rejectedWrite = false;
            boolean acknowledged = false;
            boolean verificationFailed = false;
            int lastWrite = -1;
            int lastVerification = -1;
            for (int i = 0; i < steps.size(); i++) {
                var step = steps.get(i);
                if (hasWrite(step, SnmpCommandResult.Status.SUCCEEDED)) {
                    acknowledged = true;
                    lastWrite = i;
                }
                rejectedWrite |= hasWrite(step, SnmpCommandResult.Status.REJECTED);
                if (verifiesWrite(step, lastWrite == i)) lastVerification = i;
                verificationFailed |= step.status() == SnmpStepResult.Status.VERIFICATION_FAILED;
            }
            if (!acknowledged) return rejectedWrite ? SnmpTaskResult.WriteState.REJECTED : SnmpTaskResult.WriteState.NOT_SUBMITTED;
            if (verificationFailed) return SnmpTaskResult.WriteState.VERIFICATION_FAILED;
            // A successful verification before a later write cannot confirm that later write.
            return lastVerification >= lastWrite ? SnmpTaskResult.WriteState.VERIFIED : SnmpTaskResult.WriteState.ACKNOWLEDGED_UNVERIFIED;
        }

        private static boolean uncertainWrite(SnmpCommandResult command) {
            return command.operation().equals("SET") && command.submissions() > 0
                    && (command.status() == SnmpCommandResult.Status.WRITE_OUTCOME_UNKNOWN || command.status() == SnmpCommandResult.Status.RUNNING);
        }

        private static boolean hasWrite(SnmpStepResult<?> step, SnmpCommandResult.Status status) {
            return step.commands().stream().anyMatch(command -> command.operation().equals("SET")
                    && command.submissions() > 0 && command.status() == status);
        }

        private static boolean verifiesWrite(SnmpStepResult<?> step, boolean wroteInStep) {
            return step.status() == SnmpStepResult.Status.SUCCEEDED
                    && (step.kind() == SnmpStep.Kind.VERIFY || step.kind() == SnmpStep.Kind.WRITE
                    && wroteInStep && step.value() instanceof SnmpWriteResult<?>);
        }
    }

    /** Worker-produced values and command history, copied into step snapshots. */
    static final class MutableStep<T> {
        final SnmpStep<T> definition;
        SnmpStepResult.Status status = SnmpStepResult.Status.NOT_STARTED;
        Instant startedAt;
        Instant finishedAt;
        T value;
        SnmpFailure failure;
        final List<MutableCommand> commands = new ArrayList<>();
        MutableStep(SnmpStep<T> definition) { this.definition = definition; }
        SnmpStepResult<T> snapshot() { return new SnmpStepResult<>(definition, status, startedAt, finishedAt, value, commands.stream().map(MutableCommand::snapshot).toList(), failure); }
    }

    /** Mutable evidence for one logical call; submissions may grow during a table walk. */
    static final class MutableCommand {
        final int sequence;
        final String operation;
        final List<SnmpOid> oids;
        final List<SnmpBinding> bindings;
        final Instant startedAt = Instant.now();
        Instant finishedAt;
        int submissions;
        SnmpCommandResult.Status status = SnmpCommandResult.Status.RUNNING;
        List<SnmpBinding> response = List.of();
        SnmpFailure failure;
        MutableCommand(int sequence, String operation, List<SnmpOid> oids, List<SnmpBinding> bindings) {
            this.sequence = sequence;
            this.operation = operation;
            this.oids = oids;
            this.bindings = bindings;
        }
        SnmpCommandResult snapshot() { return new SnmpCommandResult(sequence, operation, status, startedAt, finishedAt, submissions, oids, bindings, response, failure); }
    }
}
