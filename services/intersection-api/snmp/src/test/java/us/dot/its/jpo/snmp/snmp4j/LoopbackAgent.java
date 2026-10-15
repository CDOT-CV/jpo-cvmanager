package us.dot.its.jpo.snmp.snmp4j;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.snmp4j.*;
import org.snmp4j.mp.CounterSupport;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import us.dot.its.jpo.snmp.client.SnmpCredentials;
import us.dot.its.jpo.snmp.client.SnmpTarget;
import us.dot.its.jpo.snmp.rsu.ntcip1218.ForwardingTable;

/** Disposable real UDP/SNMPv3 agent. No registry singletons, Docker, or external devices. */
final class LoopbackAgent implements AutoCloseable, CommandResponder {
    final ConcurrentSkipListMap<OID, Variable> values = new ConcurrentSkipListMap<>();
    final AtomicInteger reads = new AtomicInteger(), writes = new AtomicInteger();
    final AtomicInteger active = new AtomicInteger(), maximum = new AtomicInteger();
    private final ConcurrentLinkedQueue<Runnable> held = new ConcurrentLinkedQueue<>();
    private final java.util.concurrent.ScheduledExecutorService delayed = Executors.newSingleThreadScheduledExecutor();
    private final Snmp snmp;
    private final DefaultUdpTransportMapping transport;
    final SnmpCredentials credentials;
    volatile boolean dropReads, dropWrites, hold, rejectWrites;
    volatile long delayMillis;
    volatile Consumer<PDU> alterResponse = ignored -> {};

    LoopbackAgent(SnmpCredentials credentials) throws IOException { this(credentials, 0, 1, "800000000102030405"); }
    LoopbackAgent(SnmpCredentials credentials, int port, int boots, String engineId) throws IOException {
        this.credentials = credentials;
        var protocols = new SecurityProtocols(SecurityProtocols.SecurityProtocolSet.none);
        protocols.addAuthenticationProtocol(new AuthSHA());
        protocols.addAuthenticationProtocol(new AuthHMAC192SHA256());
        protocols.addAuthenticationProtocol(new AuthHMAC384SHA512());
        protocols.addPrivacyProtocol(new PrivAES128());
        protocols.addPrivacyProtocol(new PrivDES());
        var engine = new OctetString(java.util.HexFormat.of().parseHex(engineId));
        var counters = new CounterSupport();
        var usm = new USM(protocols, engine, boots, counters);
        OID auth = switch (credentials.authentication()) {
            case SHA -> AuthSHA.ID;
            case SHA_256 -> AuthHMAC192SHA256.ID;
            case SHA_512 -> AuthHMAC384SHA512.ID;
        };
        usm.addUser(new UsmUser(new OctetString(credentials.username()), auth,
                new OctetString(credentials.authenticationPassword()),
                credentials.privacy() == SnmpCredentials.Privacy.AES ? PrivAES128.ID : PrivDES.ID,
                new OctetString(credentials.privacyPassword())));
        var dispatcher = new MessageDispatcherImpl();
        dispatcher.addMessageProcessingModel(new MPv3(engine.getValue(), new DefaultPDUFactory(), protocols,
                new SecurityModels().addSecurityModel(usm), counters));
        transport = new DefaultUdpTransportMapping(new UdpAddress("127.0.0.1/" + port), true);
        snmp = new Snmp(dispatcher, transport);
        snmp.setLocalEngine(engine.getValue(), boots, 0);
        snmp.addCommandResponder(this);
        snmp.listen();
    }
    int port() { return transport.getListenAddress().getPort(); }
    SnmpTarget target() { return new SnmpTarget("127.0.0.1", port(), credentials); }
    void integer(String oid, int value) { values.put(new OID(oid), new Integer32(value)); }

    @Override public <A extends Address> void processPdu(CommandResponderEvent<A> event) {
        PDU request = event.getPDU();
        if (!(request instanceof ScopedPDU)) return;
        event.setProcessed(true);
        PDU response = (PDU) request.clone();
        response.setType(PDU.RESPONSE);
        response.setErrorStatus(PDU.noError);
        response.setErrorIndex(0);
        response.clear();
        // clear() also resets the request ID in some versions; explicitly retain correlation.
        response.setRequestID(request.getRequestID());
        boolean write = request.getType() == PDU.SET;
        if (write) writes.incrementAndGet(); else reads.incrementAndGet();
        int count = active.incrementAndGet(); maximum.accumulateAndGet(count, Math::max);
        if (write && rejectWrites) {
            response.addAll(request.toArray());
            response.setErrorStatus(PDU.notWritable);
            response.setErrorIndex(1);
        } else {
            for (VariableBinding binding : request.getVariableBindings()) {
                if (write) {
                    values.put(new OID(binding.getOid()), (Variable) binding.getVariable().clone());
                    response.add((VariableBinding) binding.clone());
                } else if (request.getType() == PDU.GETNEXT) {
                    var next = values.higherEntry(binding.getOid());
                    response.add(next == null ? new VariableBinding(binding.getOid(), Null.endOfMibView)
                            : new VariableBinding(new OID(next.getKey()), (Variable) next.getValue().clone()));
                } else {
                    Variable value = values.get(binding.getOid());
                    response.add(new VariableBinding(new OID(binding.getOid()), value == null ? Null.noSuchInstance : (Variable) value.clone()));
                }
            }
            if (write) applyRowStatus(request);
        }
        if (write ? dropWrites : dropReads) { active.decrementAndGet(); return; }
        alterResponse.accept(response);
        Runnable send = () -> {
            try {
                event.getMessageDispatcher().returnResponsePdu(event.getMessageProcessingModel(), event.getSecurityModel(),
                        event.getSecurityName(), event.getSecurityLevel(), response, event.getMaxSizeResponsePDU(),
                        event.getStateReference(), new StatusInformation());
            } catch (MessageException failure) {
                throw new AssertionError(failure);
            } finally { active.decrementAndGet(); }
        };
        if (hold) held.add(send);
        else if (delayMillis > 0) delayed.schedule(send, delayMillis, TimeUnit.MILLISECONDS);
        else send.run();
    }

    private void applyRowStatus(PDU request) {
        for (ForwardingTable table : ForwardingTable.values()) {
            for (VariableBinding binding : request.getVariableBindings()) {
                String prefix = table.root().value() + ".1." + table.statusColumn() + ".";
                if (!binding.getOid().toString().startsWith(prefix)) continue;
                if (binding.getVariable().toInt() == 4) values.put(binding.getOid(), new Integer32(1));
                if (binding.getVariable().toInt() == 6) {
                    int index = binding.getOid().last();
                    for (int column = 2; column <= table.lastColumn(); column++) values.remove(new OID(table.cell(column, index).value()));
                }
            }
        }
    }

    void release() {
        hold = false;
        Runnable action;
        while ((action = held.poll()) != null) action.run();
    }
    @Override public void close() throws IOException { held.clear(); delayed.shutdownNow(); snmp.close(); }
}
