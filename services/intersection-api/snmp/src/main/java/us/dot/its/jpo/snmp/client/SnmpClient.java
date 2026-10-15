package us.dot.its.jpo.snmp.client;

import java.util.List;

/** Thread-safe clients; no application-level replay of writes. */
public interface SnmpClient extends AutoCloseable {
    /** Non-owning, synchronous view honoring a shared deadline, policy and submission accounting. */
    default SnmpClient scoped(SnmpCallContext context) {
        throw new UnsupportedOperationException("This client does not support scoped execution");
    }
    /** Safe concurrency for independent credential profiles, used by the task executor. */
    default int concurrencyLimit() { return 1; }
    List<SnmpBinding> get(SnmpTarget target, List<SnmpOid> oids);
    List<SnmpBinding> getNext(SnmpTarget target, List<SnmpOid> oids);
    List<SnmpBinding> walk(SnmpTarget target, SnmpOid subtree);
    /** Success acknowledges the agent response, not persistence or verified device behavior. */
    List<SnmpBinding> set(SnmpTarget target, List<SnmpBinding> bindings);
    /** Release sockets and cancel pending requests. Implementations without resources need no action. */
    @Override default void close() {}
}
