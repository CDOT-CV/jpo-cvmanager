package us.dot.its.jpo.snmp.snmp4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.LongAdder;
import org.snmp4j.PDU;
import org.snmp4j.ScopedPDU;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.dot.its.jpo.snmp.client.*;
import us.dot.its.jpo.snmp.internal.OperationLog;
import static us.dot.its.jpo.snmp.client.SnmpException.Kind.*;

/**
 * Reusable SNMPv3 adapter with bounded operations, credential-profile caches and table reads.
 * Security state belongs to each session, not SNMP4J global registries.
 * Share the client across callers and close it after any executor that borrows it.
 * @see SnmpClient
 * @see Snmp4jOptions
 */
public final class Snmp4jClient implements SnmpClient {
    private static final Logger log = LoggerFactory.getLogger(Snmp4jClient.class);
    private final Snmp4jOptions options;
    private final ManagedSession.TransportFactory transportFactory;
    private final Semaphore permits;
    private final LinkedHashMap<String, ManagedSession> sessions = new LinkedHashMap<>(16, 0.75f, true);
    private boolean closed;
    private final LongAdder completed = new LongAdder();
    private final LongAdder failed = new LongAdder();

    /** Creates a reusable client with the default request, cache and concurrency limits. */
    public Snmp4jClient() { this(Snmp4jOptions.defaults()); }

    /**
     * Creates a client using explicit limits; sessions and sockets are opened when first needed.
     * @param options request and resource limits
     */
    public Snmp4jClient(Snmp4jOptions options) {
        this(options, DefaultUdpTransportMapping::new);
    }

    // Package-local transport injection keeps deterministic network-failure tests off the public API.
    Snmp4jClient(Snmp4jOptions options, ManagedSession.TransportFactory transportFactory) {
        this.options = Objects.requireNonNull(options);
        this.transportFactory = Objects.requireNonNull(transportFactory);
        permits = new Semaphore(options.maxConcurrentOperations(), true);
    }

    @Override public int concurrencyLimit() { return Math.min(options.maxConcurrentOperations(), options.maxSessions()); }

    @Override public SnmpClient scoped(SnmpCallContext context) {
        Objects.requireNonNull(context);
        return new SnmpClient() {
            @Override public List<SnmpBinding> get(SnmpTarget target, List<SnmpOid> oids) { return read(target, oids, PDU.GET, context); }
            @Override public List<SnmpBinding> getNext(SnmpTarget target, List<SnmpOid> oids) { return read(target, oids, PDU.GETNEXT, context); }
            @Override public List<SnmpBinding> set(SnmpTarget target, List<SnmpBinding> bindings) { return Snmp4jClient.this.set(target, bindings, context); }
            @Override public List<SnmpBinding> walk(SnmpTarget target, SnmpOid subtree) { return Snmp4jClient.this.walk(target, subtree, context); }
        };
    }

    @Override public List<SnmpBinding> get(SnmpTarget target, List<SnmpOid> oids) {
        return read(target, oids, PDU.GET, null);
    }
    @Override public List<SnmpBinding> getNext(SnmpTarget target, List<SnmpOid> oids) {
        return read(target, oids, PDU.GETNEXT, null);
    }
    private List<SnmpBinding> read(SnmpTarget target, List<SnmpOid> oids, int type, SnmpCallContext context) {
        var requested = List.copyOf(oids);
        requireUnique(requested);
        return operation(target, PDU.getTypeString(type), context, (session, deadline, trace) -> exchange(session, target, request(type, requested), deadline, trace, context));
    }

    @Override public List<SnmpBinding> set(SnmpTarget target, List<SnmpBinding> bindings) {
        return set(target, bindings, null);
    }
    private List<SnmpBinding> set(SnmpTarget target, List<SnmpBinding> bindings, SnmpCallContext context) {
        var requested = List.copyOf(bindings);
        requireUnique(requested.stream().map(SnmpBinding::oid).toList());
        var pdu = new ScopedPDU();
        pdu.setType(PDU.SET);
        requested.forEach(binding -> pdu.add(new VariableBinding(new OID(binding.oid().value()), Snmp4jCodec.encode(binding.value()))));
        return operation(target, "SET", context, (session, deadline, trace) -> {
            var result = exchange(session, target, pdu, deadline, trace, context);
            if (!requested.equals(result)) throw new SnmpException(INVALID_OUTPUT, true);
            return result;
        });
    }

    @Override public List<SnmpBinding> walk(SnmpTarget target, SnmpOid subtree) {
        return walk(target, subtree, null);
    }
    private List<SnmpBinding> walk(SnmpTarget target, SnmpOid subtree, SnmpCallContext context) {
        Objects.requireNonNull(subtree);
        return operation(target, "WALK", context, (session, deadline, trace) -> {
            var walk = new WalkState(subtree);
            while (true) {
                List<SnmpBinding> batch;
                try {
                    batch = exchange(session, target, walk.nextRequest(), deadline, trace, context);
                } catch (SnmpException failure) {
                    if (!walk.reducePageSize(failure)) throw failure;
                    continue;
                }
                if (walk.accept(batch)) return List.copyOf(walk.bindings);
            }
        });
    }

    /** One walk's progress and limits; never shared across calls or device sessions. */
    private final class WalkState {
        private final SnmpOid subtree;
        private final List<SnmpBinding> bindings = new ArrayList<>();
        private SnmpOid cursor;
        private long bytes;
        private int repetitions = options.maxBulkRepetitions();

        private WalkState(SnmpOid subtree) { this.subtree = subtree; cursor = subtree; }

        private ScopedPDU nextRequest() {
            var next = request(repetitions == 0 ? PDU.GETNEXT : PDU.GETBULK, List.of(cursor));
            if (repetitions > 0) {
                next.setNonRepeaters(0);
                next.setMaxRepetitions(repetitions);
            }
            return next;
        }

        private boolean reducePageSize(SnmpException failure) {
            // Only an explicit size rejection permits another page attempt. The cursor and
            // shared deadline stay unchanged; timeouts and malformed replies are never hidden.
            if (repetitions == 0 || failure.kind() != AGENT_REJECTED
                    || failure.agentErrorStatus().orElse(-1) != PDU.tooBig) return false;
            repetitions = repetitions == 1 ? 0 : repetitions / 2;
            return true;
        }

        private boolean accept(List<SnmpBinding> batch) {
            for (var binding : batch) {
                if (walkEnded(binding, subtree)) return true;
                bytes += binding.oid().value().length() + (binding.value() instanceof SnmpValue.Octets octets ? octets.hex().length() / 2 : 32);
                if (bindings.size() >= options.maxWalkBindings() || bytes > options.maxWalkBytes()) throw new SnmpException(OUTPUT_LIMIT, false);
                bindings.add(binding);
                cursor = binding.oid();
            }
            return false;
        }
    }

    private static boolean walkEnded(SnmpBinding binding, SnmpOid subtree) {
        if (!(binding.value() instanceof SnmpValue.ExceptionValue exception)) return !binding.oid().isWithin(subtree);
        if (exception.kind() != SnmpValue.ExceptionValue.Kind.END_OF_MIB_VIEW) throw new SnmpException(INVALID_OUTPUT, false);
        return true;
    }

    private List<SnmpBinding> exchange(ManagedSession session, SnmpTarget target, ScopedPDU request, long deadline, OperationLog trace, SnmpCallContext context) {
        var address = new UdpAddress(target.address() + "/" + target.port());
        var recovery = session.recovery(address);
        if (request.getType() != PDU.SET) invalidateStalePeer(session, address, recovery, deadline, trace, context);
        boolean knownPeer = session.knowsPeer(address);
        long generation = recovery.generation;
        try {
            return exchangeOnce(session, target, request, deadline, trace, context);
        } catch (SnmpException failure) {
            trace.requestFailed(failure);
            if (request.getType() == PDU.SET || !knownPeer || failure.kind() != TIMEOUT) throw failure;
            if (context != null && !context.policy().rediscoverOnTimeout()) {
                // No extra traffic now; the next read starts fresh rather than staying pinned to an old engine forever.
                recovery.staleGeneration.accumulateAndGet(generation, Math::max);
                throw failure;
            }
            // A replaced agent can leave cached authoritative/context engine IDs stale. Retry only reads,
            // once, within the original operation deadline, with fresh endpoint discovery.
            checkDeadline(deadline, false);
            return withRecoveryLock(recovery, deadline, context, () -> {
                if (recovery.generation == generation) {
                    trace.event("read_rediscovery_started");
                    session.forgetPeer(address);
                    recovery.generation++;
                } else trace.event("read_rediscovery_joined");
                var fresh = (ScopedPDU) request.clone();
                fresh.setRequestID(new org.snmp4j.smi.Integer32(0));
                fresh.setContextEngineID(new org.snmp4j.smi.OctetString());
                return exchangeOnce(session, target, fresh, deadline, trace, context);
            });
        }
    }

    private static void invalidateStalePeer(ManagedSession session, UdpAddress address,
            ManagedSession.PeerRecovery recovery, long deadline, OperationLog trace, SnmpCallContext context) {
        if (recovery.staleGeneration.get() < recovery.generation) return;
        withRecoveryLock(recovery, deadline, context, () -> {
            if (recovery.staleGeneration.get() >= recovery.generation) {
                session.forgetPeer(address);
                recovery.generation++;
                trace.event("read_discovery_cache_invalidated");
            }
            return null;
        });
    }

    private static <T> T withRecoveryLock(ManagedSession.PeerRecovery recovery, long deadline,
            SnmpCallContext context, java.util.function.Supplier<T> action) {
        try {
            if (!recovery.lock.tryLock(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) throw new SnmpException(TIMEOUT, false);
            try { return action.get(); }
            finally { recovery.lock.unlock(); }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw stopped(context, CANCELLED, false);
        }
    }

    private List<SnmpBinding> exchangeOnce(ManagedSession session, SnmpTarget target, ScopedPDU request, long deadline, OperationLog trace, SnmpCallContext context) {
        boolean write = request.getType() == PDU.SET;
        checkDeadline(deadline, false);
        var policy = context == null ? new SnmpRequestPolicy(options.requestTimeout(), options.readRetries(), true) : context.policy();
        int retries = write ? 0 : policy.readRetries();
        // Preserve the configured timeout per attempt. Compressing retries into the remaining
        // budget can expire early on SNMP4J's timer; the monotonic future wait below bounds the operation.
        long timeout = policy.requestTimeout().toMillis();
        var destination = session.target(target.address(), target.port(), timeout, retries);
        ManagedSession.Pending pending = null;
        if (context != null) context.beforeRequest(write);
        try {
            trace.requestSubmitting(PDU.getTypeString(request.getType()), request.size(), timeout, retries);
            pending = session.send(request, destination);
            trace.event("request_submitted");
            ResponseEvent<UdpAddress> event = pending.response.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (event.getError() != null) throw new SnmpException(EXECUTION, write);
            if (event.getResponse() == null) throw stopped(context, TIMEOUT, write);
            if (!destination.getAddress().equals(event.getPeerAddress())) throw new SnmpException(INVALID_OUTPUT, write);
            var result = Snmp4jResponseValidator.validate(request, event.getResponse());
            trace.event("request_response_validated");
            return result;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw stopped(context, CANCELLED, write);
        } catch (TimeoutException failure) {
            throw stopped(context, TIMEOUT, write);
        } catch (IOException failure) {
            throw new SnmpException(EXECUTION, write);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof SnmpException safe) throw safe.withWriteOutcomeUnknown(write);
            throw new SnmpException(EXECUTION, write);
        } finally {
            if (pending != null) pending.cancel();
        }
    }

    private static SnmpException stopped(SnmpCallContext context, SnmpException.Kind fallback, boolean write) {
        if (context != null) {
            try { context.remainingNanos(); }
            catch (SnmpException failure) { return failure.withWriteOutcomeUnknown(write || failure.writeOutcomeUnknown()); }
        }
        return new SnmpException(fallback, write);
    }

    private static ScopedPDU request(int type, List<SnmpOid> oids) {
        var request = new ScopedPDU();
        request.setType(type);
        oids.forEach(oid -> request.add(new VariableBinding(new OID(oid.value()))));
        return request;
    }

    /** One admission, session lease and shared deadline for the entire logical operation. */
    private <T> T operation(SnmpTarget target, String name, SnmpCallContext context, Operation<T> action) {
        Objects.requireNonNull(target);
        // A task may have already spent time on earlier steps; a new call cannot reset its budget.
        long budget = context == null ? options.operationDeadline().toNanos()
                : Math.min(options.operationDeadline().toNanos(), context.remainingNanos());
        long deadline = System.nanoTime() + budget;
        boolean admitted = false;
        ManagedSession session = null;
        try (var trace = new OperationLog(log, target, name, name.equals("SET"))) {
            try {
                admitted = permits.tryAcquire(Math.min(options.admissionTimeout().toNanos(), budget), TimeUnit.NANOSECONDS);
                if (!admitted) throw new SnmpException(BUSY, false);

                trace.step("session_acquisition");
                checkDeadline(deadline, false);
                session = acquire(target);

                trace.step("exchange");
                T result = action.execute(session, deadline, trace);
                completed.increment();

                trace.succeeded();
                return result;
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                failed.increment();
                var cancelled = stopped(context, CANCELLED, false);
                trace.failed(cancelled);
                throw cancelled;
            } catch (SnmpException failure) {
                failed.increment();
                trace.failed(failure);
                throw failure;
            } catch (RuntimeException failure) {
                trace.failedLocally();
                throw failure;
            } finally {
                if (session != null) release(session, target);
                if (admitted) permits.release();
            }
        }
    }

    /** Uses the pool monitor only for cache ownership and leases; request waits happen outside it. */
    private synchronized ManagedSession acquire(SnmpTarget target) {
        if (closed) throw new SnmpException(CANCELLED, false);
        String key = credentialKey(target.credentials());
        ManagedSession session = sessions.get(key);

        if (session != null && session.broken()) {
            // Do not hand a failed shared transport to another caller while existing calls unwind.
            if (session.leases != 0) throw new SnmpException(BUSY, false);
            sessions.remove(key);
            closeSession(session);
            session = null;
        }
        if (session == null) {
            if (sessions.size() >= options.maxSessions()) {
                var idle = sessions.entrySet().stream().filter(entry -> entry.getValue().leases == 0).findFirst();
                if (idle.isEmpty()) throw new SnmpException(BUSY, false);
                sessions.remove(idle.get().getKey());
                closeSession(idle.get().getValue());
            }
            try {
                session = new ManagedSession(target.credentials(), options.maxTargetsPerSession(), transportFactory);
            } catch (IOException failure) {
                throw new SnmpException(EXECUTION, false);
            }
            sessions.put(key, session);
        }

        session.retainPeer(new UdpAddress(target.address() + "/" + target.port()));
        session.leases++;
        return session;
    }

    private synchronized void release(ManagedSession session, SnmpTarget target) {
        session.releasePeer(new UdpAddress(target.address() + "/" + target.port()));
        session.leases--;
    }

    /** Identical full profiles may share a session; matching usernames alone are insufficient. */
    private static String credentialKey(SnmpCredentials credentials) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : List.of(credentials.username(), credentials.authenticationPassword(), credentials.privacyPassword(),
                    credentials.authentication().name(), credentials.privacy().name())) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                // Length prefixes keep different field boundaries from producing the same input.
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new SnmpException(EXECUTION, false);
        }
    }

    private static void requireUnique(List<SnmpOid> oids) {
        if (oids.isEmpty() || new HashSet<>(oids).size() != oids.size()) throw new IllegalArgumentException("Nonempty distinct OIDs required");
    }

    private static void checkDeadline(long deadline, boolean write) {
        if (System.nanoTime() >= deadline) throw new SnmpException(TIMEOUT, write);
        if (Thread.currentThread().isInterrupted()) throw new SnmpException(CANCELLED, write);
    }

    /**
     * A point-in-time view of this client's resource use. Counts logical operations, not device tasks;
     * application outcomes belong in task results. Pending requests exclude completed listeners.
     */
    public record Diagnostics(long completedOperations, long failedOperations, int activeOperations, int cachedSessions, int pendingRequests) {}

    /**
     * Returns current operation, session and pending-request counts without contacting devices.
     * @return resource-use snapshot
     */
    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(completed.sum(), failed.sum(), options.maxConcurrentOperations() - permits.availablePermits(),
                sessions.size(), sessions.values().stream().mapToInt(ManagedSession::pendingCount).sum());
    }

    /** Cancels pending requests and releases every cached session; safe to call repeatedly. */
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        boolean error = false;
        for (ManagedSession session : sessions.values()) {
            try { session.close(); } catch (IOException failure) { error = true; }
        }
        sessions.clear();
        if (error) throw new SnmpException(EXECUTION, false);
    }

    private static void closeSession(ManagedSession session) {
        try { session.close(); } catch (IOException failure) { throw new SnmpException(EXECUTION, false); }
    }

    /** One logical operation with a leased session and a shared deadline. */
    @FunctionalInterface private interface Operation<T> { T execute(ManagedSession session, long deadline, OperationLog trace); }
}
