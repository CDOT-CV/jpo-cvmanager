package us.dot.its.jpo.snmp.client;

/**
 * Adapter-facing scope for one synchronous task execution. Normally supplied by the executor.
 * Implementations must reject an exhausted deadline or request budget before sending traffic.
 */
public interface SnmpCallContext {
    /**
     * Returns the request policy for this execution.
     * @return request timeout, retries and recovery policy
     */
    SnmpRequestPolicy policy();

    /**
     * Returns the remaining shared execution budget.
     * @return remaining nanoseconds
     * @throws SnmpException with TIMEOUT or CANCELLED when execution must stop
     */
    long remainingNanos();

    /**
     * Accounts for one adapter submission immediately before sending, including walk pages
     * and rediscovery retries. Protocol retransmissions are not separate adapter submissions.
     * @param write whether this submission is a SET
     * @throws SnmpException if the execution deadline or request budget is exhausted
     */
    void beforeRequest(boolean write);
}
