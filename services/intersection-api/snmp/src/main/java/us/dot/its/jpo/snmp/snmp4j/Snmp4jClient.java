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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.dot.its.jpo.snmp.client.*;
import us.dot.its.jpo.snmp.internal.OperationLog;
import static us.dot.its.jpo.snmp.client.SnmpException.Kind.*;

/** Thread-safe, bounded SNMPv3 client. No global SNMP4J registries or application worker pools. */
public final class Snmp4jClient implements SnmpClient {
    private static final Logger log = LoggerFactory.getLogger(Snmp4jClient.class);
    private final Snmp4jOptions options;
    private final Semaphore permits;
    private final LinkedHashMap<String, ManagedSession> sessions = new LinkedHashMap<>(16, 0.75f, true);
    private boolean closed;
    private final LongAdder completed = new LongAdder();
    private final LongAdder failed = new LongAdder();

    public Snmp4jClient() { this(Snmp4jOptions.defaults()); }
    public Snmp4jClient(Snmp4jOptions options) {
        this.options = Objects.requireNonNull(options);
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
            List<SnmpBinding> bindings = new ArrayList<>();
            SnmpOid cursor = subtree;
            long bytes = 0;
            while (true) {
                var binding = exchange(session, target, request(PDU.GETNEXT, List.of(cursor)), deadline, trace, context).getFirst();
                if (binding.value() instanceof SnmpValue.ExceptionValue exception) {
                    if (exception.kind() == SnmpValue.ExceptionValue.Kind.END_OF_MIB_VIEW) return List.copyOf(bindings);
                    throw new SnmpException(INVALID_OUTPUT, false);
                }
                if (!binding.oid().isWithin(subtree)) return List.copyOf(bindings);
                bytes += binding.oid().value().length() + (binding.value() instanceof SnmpValue.Octets octets ? octets.hex().length() / 2 : 32);
                if (bindings.size() >= options.maxWalkBindings() || bytes > options.maxWalkBytes()) throw new SnmpException(OUTPUT_LIMIT, false);
                bindings.add(binding);
                cursor = binding.oid();
            }
        });
    }

    private List<SnmpBinding> exchange(ManagedSession session, SnmpTarget target, ScopedPDU request, long deadline, OperationLog trace, SnmpCallContext context) {
        var address = new UdpAddress(target.address() + "/" + target.port());
        var recovery = session.recovery(address);
        if (request.getType() != PDU.SET && recovery.staleGeneration.get() >= recovery.generation) {
            try {
                if (!recovery.lock.tryLock(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) throw new SnmpException(TIMEOUT, false);
                try {
                    if (recovery.staleGeneration.get() >= recovery.generation) {
                        session.forgetPeer(address);
                        recovery.generation++;
                        trace.event("read_discovery_cache_invalidated");
                    }
                } finally { recovery.lock.unlock(); }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw stopped(context, CANCELLED, false);
            }
        }
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
            try {
                if (!recovery.lock.tryLock(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) throw new SnmpException(TIMEOUT, false);
                try {
                    if (recovery.generation == generation) {
                        trace.event("read_rediscovery_started");
                        session.forgetPeer(address);
                        recovery.generation++;
                    } else trace.event("read_rediscovery_joined");
                    var fresh = (ScopedPDU) request.clone();
                    fresh.setRequestID(new org.snmp4j.smi.Integer32(0));
                    fresh.setContextEngineID(new org.snmp4j.smi.OctetString());
                    return exchangeOnce(session, target, fresh, deadline, trace, context);
                } finally { recovery.lock.unlock(); }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw stopped(context, CANCELLED, false);
            }
        }
    }

    private List<SnmpBinding> exchangeOnce(ManagedSession session, SnmpTarget target, ScopedPDU request, long deadline, OperationLog trace, SnmpCallContext context) {
        boolean write = request.getType() == PDU.SET;
        checkDeadline(deadline, false);
        var policy = context == null ? new SnmpRequestPolicy(options.requestTimeout(), options.readRetries(), true) : context.policy();
        int retries = write ? 0 : policy.readRetries();
        long attemptBudget = Math.max(1, (deadline - System.nanoTime()) / (retries + 1));
        // Round up: rounding down can report a timeout just before the shared deadline expires.
        long timeout = Math.max(1, Math.min(policy.requestTimeout().toMillis(),
                Math.ceilDiv(attemptBudget, TimeUnit.MILLISECONDS.toNanos(1))));
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
            var result = validate(request, event.getResponse());
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

    private static List<SnmpBinding> validate(ScopedPDU request, PDU response) {
        boolean write = request.getType() == PDU.SET;
        if (response.getType() == PDU.REPORT) throw new SnmpException(AUTHENTICATION, false);
        if (!(response instanceof ScopedPDU scoped) || response.getType() != PDU.RESPONSE
                || !Objects.equals(request.getRequestID(), response.getRequestID())
                || !request.getContextName().equals(scoped.getContextName())) throw new SnmpException(INVALID_OUTPUT, write);
        if (response.getErrorStatus() != PDU.noError) {
            int status = response.getErrorStatus();
            boolean uncertain = write && (status == PDU.undoFailed || status == PDU.commitFailed || status == PDU.genErr);
            throw new SnmpException(AGENT_REJECTED, uncertain, status, response.getErrorIndex());
        }
        if (response.size() != request.size()) throw new SnmpException(INVALID_OUTPUT, write);
        List<SnmpBinding> values = new ArrayList<>();
        try {
            for (int i = 0; i < response.size(); i++) {
                var actual = response.get(i);
                var oid = new SnmpOid(actual.getOid().toString());
                var value = Snmp4jCodec.decode(actual.getVariable());
                var expected = request.get(i).getOid();
                if (request.getType() == PDU.GETNEXT) {
                    if (!(value instanceof SnmpValue.ExceptionValue) && actual.getOid().compareTo(expected) <= 0) throw new SnmpException(INVALID_OUTPUT, write);
                } else if (!actual.getOid().equals(expected)) throw new SnmpException(INVALID_OUTPUT, write);
                values.add(new SnmpBinding(oid, value));
            }
            return List.copyOf(values);
        } catch (SnmpException failure) {
            throw failure.withWriteOutcomeUnknown(write);
        } catch (IllegalArgumentException failure) {
            throw new SnmpException(INVALID_OUTPUT, write);
        }
    }

    private static ScopedPDU request(int type, List<SnmpOid> oids) {
        var request = new ScopedPDU();
        request.setType(type);
        oids.forEach(oid -> request.add(new VariableBinding(new OID(oid.value()))));
        return request;
    }

    private <T> T operation(SnmpTarget target, String name, SnmpCallContext context, Operation<T> action) {
        Objects.requireNonNull(target);
        long budget = context == null ? options.operationDeadline().toNanos() : Math.min(options.operationDeadline().toNanos(), context.remainingNanos());
        long deadline = System.nanoTime() + budget;
        boolean admitted = false;
        ManagedSession session = null;
        try (var trace = new OperationLog(log, target, name, name.equals("SET"))) {
            try {
                admitted = permits.tryAcquire(Math.min(options.admissionTimeout().toNanos(), budget), TimeUnit.NANOSECONDS);
                if (!admitted) throw new SnmpException(BUSY, false);
                trace.stepSucceeded();
                trace.step("session_acquisition");
                checkDeadline(deadline, false);
                session = acquire(target);
                trace.stepSucceeded();
                trace.step("exchange");
                T result = action.execute(session, deadline, trace);
                completed.increment();
                trace.stepSucceeded();
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
                if (session != null) release(session);
                if (admitted) permits.release();
            }
        }
    }

    private synchronized ManagedSession acquire(SnmpTarget target) {
        if (closed) throw new SnmpException(CANCELLED, false);
        String key = credentialKey(target.credentials());
        ManagedSession session = sessions.get(key);
        if (session != null && !session.targets.contains(target.agent()) && session.targets.size() >= options.maxTargetsPerSession()) {
            // Rotate only an idle profile. No in-flight request may lose its security state.
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
                session = new ManagedSession(target.credentials(), options.maxTargetsPerSession());
            } catch (IOException failure) {
                throw new SnmpException(EXECUTION, false);
            }
            sessions.put(key, session);
        }
        session.targets.add(target.agent());
        session.leases++;
        return session;
    }

    private synchronized void release(ManagedSession session) { session.leases--; }

    private static String credentialKey(SnmpCredentials credentials) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : List.of(credentials.username(), credentials.authenticationPassword(), credentials.privacyPassword(),
                    credentials.authentication().name(), credentials.privacy().name())) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
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

    /** Counts real completed operations and outstanding wire requests, not async submissions. */
    public record Diagnostics(long completedOperations, long failedOperations, int activeOperations, int cachedSessions, int pendingRequests) {}
    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(completed.sum(), failed.sum(), options.maxConcurrentOperations() - permits.availablePermits(),
                sessions.size(), sessions.values().stream().mapToInt(ManagedSession::pendingCount).sum());
    }

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
    @FunctionalInterface private interface Operation<T> { T execute(ManagedSession session, long deadline, OperationLog trace); }
}
