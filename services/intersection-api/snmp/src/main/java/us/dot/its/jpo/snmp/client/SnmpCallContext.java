package us.dot.its.jpo.snmp.client;

/** Synchronous call scope. Implementations must throw before sending if the deadline/budget is exhausted. */
public interface SnmpCallContext {
    SnmpRequestPolicy policy();
    /** Remaining execution budget, or TIMEOUT/CANCELLED if execution must stop. */
    long remainingNanos();
    /** Called immediately before each adapter submission, including WALK steps and rediscovery retries. */
    void beforeRequest(boolean write);
}
