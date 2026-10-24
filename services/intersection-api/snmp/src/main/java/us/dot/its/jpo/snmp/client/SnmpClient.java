package us.dot.its.jpo.snmp.client;

import java.util.List;

/**
 * Reusable, thread-safe SNMP operations using library types.
 * Callers own the client's lifetime; device procedures should borrow a scoped view from their task.
 * Read exception values, such as noSuchInstance, remain data for the caller to inspect.
 */
public interface SnmpClient extends AutoCloseable {
    /**
     * Borrows a synchronous view that shares the task's deadline, policy and request accounting.
     * Closing this view must not close the underlying client.
     *
     * @throws UnsupportedOperationException if the implementation cannot honor scoped execution
     */
    default SnmpClient scoped(SnmpCallContext context) {
        throw new UnsupportedOperationException("This client does not support scoped execution");
    }

    /** Safe concurrency for independent credential profiles, used by the task executor. */
    default int concurrencyLimit() { return 1; }

    /**
     * Reads known OIDs together, returning bindings in the requested order.
     *
     * @throws IllegalArgumentException if the OID list is empty or contains duplicates
     * @throws SnmpException if the request fails or the response cannot be validated
     */
    List<SnmpBinding> get(SnmpTarget target, List<SnmpOid> oids);

    /**
     * Reads the next OID after each supplied OID; primarily used to traverse tables.
     *
     * @throws IllegalArgumentException if the OID list is empty or contains duplicates
     * @throws SnmpException if the request fails or the response cannot be validated
     */
    List<SnmpBinding> getNext(SnmpTarget target, List<SnmpOid> oids);

    /**
     * Reads a subtree within the client's deadline and output limits. A failed walk must not
     * return a partial table as though it were complete.
     *
     * @throws SnmpException if a request fails, the walk is malformed, or a limit is reached
     */
    List<SnmpBinding> walk(SnmpTarget target, SnmpOid subtree);

    /**
     * Sends one SET without automatic replay. Success acknowledges the agent response;
     * the device procedure must read back state to verify the intended change.
     *
     * @throws IllegalArgumentException if the binding list is empty, has duplicate OIDs,
     *         or contains a value that cannot be written
     * @throws SnmpException if the request fails; inspect {@link SnmpException#writeOutcomeUnknown()}
     *         before deciding whether another write is safe
     */
    List<SnmpBinding> set(SnmpTarget target, List<SnmpBinding> bindings);

    /** Release sockets and cancel pending requests. Implementations without resources need no action. */
    @Override default void close() {}
}
