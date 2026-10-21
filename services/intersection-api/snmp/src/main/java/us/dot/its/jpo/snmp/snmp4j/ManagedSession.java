package us.dot.its.jpo.snmp.snmp4j;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.event.ResponseListener;
import org.snmp4j.mp.CounterSupport;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import us.dot.its.jpo.snmp.client.SnmpCredentials;
import us.dot.its.jpo.snmp.client.SnmpException;

/** One credential profile owns its dispatcher, USM, protocol registry, transport and timers. */
final class ManagedSession implements AutoCloseable {
    private final Snmp snmp;
    private final MPv3 mpv3;
    private final OctetString username;
    private final Set<Pending> pending = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UdpAddress, PeerRecovery> recovery = new ConcurrentHashMap<>();
    // Accessed only under the client pool monitor.
    final Set<String> targets = new HashSet<>();
    int leases;
    private volatile boolean closed;

    ManagedSession(SnmpCredentials credentials, int maxTargets) throws IOException {
        username = new OctetString(credentials.username());
        if (username.length() > 32) throw new IllegalArgumentException("SNMPv3 username exceeds 32 bytes");

        // Keep security registries private to this credential profile. Replacing SNMP4J's global
        // registries would let unrelated requests change each other's authentication state.
        var protocols = new SecurityProtocols(SecurityProtocols.SecurityProtocolSet.none);
        protocols.addAuthenticationProtocol(new AuthSHA());
        protocols.addAuthenticationProtocol(new AuthHMAC192SHA256());
        protocols.addAuthenticationProtocol(new AuthHMAC384SHA512());
        protocols.addPrivacyProtocol(new PrivAES128());
        protocols.addPrivacyProtocol(new PrivDES());
        var engine = new OctetString(MPv3.createLocalEngineID());
        var counters = new CounterSupport();
        var usm = new USM(protocols, engine, 0, counters);
        usm.addUser(new UsmUser(username, authentication(credentials.authentication()),
                new OctetString(credentials.authenticationPassword()), privacy(credentials.privacy()),
                new OctetString(credentials.privacyPassword())));
        var securityModels = new SecurityModels().addSecurityModel(usm);
        mpv3 = new MPv3(engine.getValue(), new DefaultPDUFactory(), protocols, securityModels, counters);
        mpv3.setMaxEngineIdCacheSize(maxTargets);
        var dispatcher = new MessageDispatcherImpl();
        dispatcher.addMessageProcessingModel(mpv3);
        var transport = new DefaultUdpTransportMapping();
        snmp = new Snmp(dispatcher, transport);
        try {
            snmp.listen();
        } catch (IOException | RuntimeException failure) {
            snmp.close();
            throw failure;
        }
    }

    private static OID authentication(SnmpCredentials.Authentication authentication) {
        return switch (authentication) {
            case SHA -> AuthSHA.ID;
            case SHA_256 -> AuthHMAC192SHA256.ID;
            case SHA_512 -> AuthHMAC384SHA512.ID;
        };
    }

    private static OID privacy(SnmpCredentials.Privacy privacy) {
        return privacy == SnmpCredentials.Privacy.AES ? PrivAES128.ID : PrivDES.ID;
    }

    UserTarget<UdpAddress> target(String address, int port, long timeout, int retries) {
        var target = new UserTarget<UdpAddress>();
        target.setAddress(new UdpAddress(address + "/" + port));
        target.setVersion(org.snmp4j.mp.SnmpConstants.version3);
        target.setSecurityLevel(SecurityLevel.AUTH_PRIV);
        target.setSecurityName(username);
        target.setTimeout(timeout);
        target.setRetries(retries);
        return target;
    }

    /** Registers a request before sending so shutdown can find and cancel it, even during send. */
    Pending send(ScopedPDU request, UserTarget<UdpAddress> target) throws IOException {
        var operation = new Pending(request);
        pending.add(operation);
        if (closed) {
            operation.cancel();
            throw new SnmpException(SnmpException.Kind.CANCELLED, false);
        }
        try {
            snmp.send(request, target, null, operation);
            if (closed) operation.cancel();
            return operation;
        } catch (IOException | RuntimeException failure) {
            operation.cancel();
            throw failure;
        }
    }

    int pendingCount() { return pending.size(); }

    boolean knowsPeer(UdpAddress address) { return mpv3.getEngineID(address) != null; }

    /** Serializes engine-cache refreshes for one endpoint, without blocking other devices. */
    static final class PeerRecovery {
        final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
        volatile long generation;
        final java.util.concurrent.atomic.AtomicLong staleGeneration = new java.util.concurrent.atomic.AtomicLong(-1);
    }

    PeerRecovery recovery(UdpAddress address) { return recovery.computeIfAbsent(address, ignored -> new PeerRecovery()); }

    void forgetPeer(UdpAddress address) {
        // Invalidate only this endpoint's discovery caches, never a shared USM or another peer's state.
        mpv3.removeEngineID(address);
        snmp.removeCachedContextEngineId(address);
    }

    /** Bridges SNMP4J callbacks to the waiting operation and owns its listener registration. */
    final class Pending implements ResponseListener {
        private final ScopedPDU request;
        final CompletableFuture<ResponseEvent<UdpAddress>> response = new CompletableFuture<>();

        Pending(ScopedPDU request) { this.request = request; }

        @Override public <A extends Address> void onResponse(ResponseEvent<A> event) {
            snmp.cancel(request, this);
            pending.remove(this);
            if (!(event.getPeerAddress() == null || event.getPeerAddress() instanceof UdpAddress)) {
                response.completeExceptionally(new SnmpException(SnmpException.Kind.INVALID_OUTPUT, false));
                return;
            }
            @SuppressWarnings("unchecked")
            var udp = (ResponseEvent<UdpAddress>) event;
            response.complete(udp);
        }

        void cancel() {
            // The future keeps its first result. Cancellation after a response, or a late response
            // after cancellation, cannot replace the outcome already delivered to the caller.
            snmp.cancel(request, this);
            pending.remove(this);
            response.completeExceptionally(new SnmpException(SnmpException.Kind.CANCELLED, request.getType() == PDU.SET));
        }
    }

    @Override public void close() throws IOException {
        closed = true;
        for (Pending operation : pending) operation.cancel();
        snmp.close();
    }
}
