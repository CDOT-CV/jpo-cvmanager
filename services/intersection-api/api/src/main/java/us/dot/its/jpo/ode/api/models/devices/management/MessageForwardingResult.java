package us.dot.its.jpo.ode.api.models.devices.management;

import us.dot.its.jpo.snmp.execution.SnmpTaskResult;
import us.dot.its.jpo.snmp.execution.SnmpTaskSummary;

/** Device outcome and database synchronization are independent. */
public record MessageForwardingResult(SnmpTaskSummary execution, SnmpTaskResult.WriteState writeState,
        Boolean changed, DatabaseSync databaseSync) {
    public enum DatabaseSync {
        UPDATED, NOT_UPDATED, IN_PROGRESS, FAILED
    }
}
