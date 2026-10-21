package us.dot.its.jpo.ode.api.services;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import us.dot.its.jpo.ode.api.config.SnmpProperties;
import us.dot.its.jpo.ode.api.models.devices.management.CreateMessageForwardingRequest;
import us.dot.its.jpo.ode.api.models.devices.management.MessageForwardingRequest;
import us.dot.its.jpo.ode.api.models.devices.management.MessageForwardingResult;
import us.dot.its.jpo.ode.api.models.devices.management.MessageForwardingResult.DatabaseSync;
import us.dot.its.jpo.snmp.client.SnmpTarget;
import us.dot.its.jpo.snmp.execution.*;
import us.dot.its.jpo.snmp.rsu.ntcip1218.ForwardingRequest;
import us.dot.its.jpo.snmp.rsu.ntcip1218.Ntcip1218ForwardingTasks;
import us.dot.its.jpo.snmp.rsu.ntcip1218.Ntcip1218ForwardingTasks.SnapshotAction;
import us.dot.its.jpo.ode.api.services.RsuMessageForwardingContextService.DeviceContext;

/**
 * Coordinates authorized forwarding batches through the shared, bounded SNMP
 * task executor.
 *
 * Each device task performs a verified create/delete, reads the
 * current configuration, then synchronizes PostgreSQL while retaining the
 * executor's per-device serialization slot.
 *
 * Responses keep device-write evidence separate from later readback or database
 * failures.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enable.api", havingValue = "true")
public class RsuMessageForwardingService {
    private final RsuMessageForwardingContextService contextService;
    private final RsuMessageForwardingSnapshotService snapshotService;
    private final SnmpTaskExecutor executor;
    private final SnmpProperties properties;
    private final Ntcip1218ForwardingTasks forwardingTasks;

    /**
     * Creates a new row using the jpo-snmp library's message-type port mapping;
     * this is not an upsert.
     */
    public Map<String, MessageForwardingResult> create(CreateMessageForwardingRequest request) {
        validateRequest(request);
        if (request.getIncludeSecurity() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "include_security is required");
        var forwarding = new ForwardingRequest(request.getMessageType(), request.getDestinationIp(),
                request.getIncludeSecurity());
        return execute(request,
                context -> forwardingTasks.createWithSnapshot(context.device(), forwarding, synchronizeDb(context)));
    }

    /**
     * Deletes the highest matching row. A confirmed absence is a successful,
     * unchanged result.
     */
    public Map<String, MessageForwardingResult> delete(MessageForwardingRequest request) {
        validateRequest(request);
        return execute(request,
                context -> forwardingTasks.deleteWithSnapshot(context.device(), request.getMessageType(),
                        request.getDestinationIp(), synchronizeDb(context)));
    }

    /**
     * Rejects DNS names, duplicate targets and invalid batches before ownership or
     * inventory lookup.
     */
    public static void validateRequest(MessageForwardingRequest request) {
        try {
            if (request.getRsuIps() == null || request.getRsuIps().isEmpty() || request.getRsuIps().size() > 100
                    || request.getMessageType() == null)
                throw new IllegalArgumentException();
            SnmpTarget.validateIpv4(request.getDestinationIp());
            request.getRsuIps().forEach(SnmpTarget::validateIpv4);
            if (request.getRsuIps().stream().distinct().count() != request.getRsuIps().size())
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Supply 1-100 unique RSU IPv4 addresses, a message_type and a destination IPv4 address.");
        }
    }

    /**
     * Resolves the whole batch before dispatching the library's forwarding recipes.
     */
    private Map<String, MessageForwardingResult> execute(MessageForwardingRequest request,
            Function<DeviceContext, SnmpTask> taskFactory) {
        var contexts = contextService.load(request.getRsuIps());
        var options = properties.taskOptions();
        var handles = new LinkedHashMap<String, SnmpTaskHandle>();
        for (var context : contexts) {
            var task = taskFactory.apply(context);
            handles.put(context.device().target().address(), executor.submit(task, options));
        }
        return awaitResults(handles);
    }

    /**
     * Creates the task callback that atomically replaces this RSU's PostgreSQL
     * forwarding snapshot with a complete device readback. This occurs when the
     * task runs the callback after successful verification and retrieval, not when
     * it is created.
     */
    private SnapshotAction synchronizeDb(DeviceContext context) {
        return new SnapshotAction("sync-database", rows -> {
            try {
                snapshotService.replace(context.rsuId(), context.device().target().address(), rows);
            } catch (RuntimeException failure) {
                // Catch outside the transactional proxy to include begin/commit failures.
                logSnapshotFailure(context, failure);
                throw failure;
            }
        });
    }

    /**
     * Logs exception types and SQL error codes without SQL values or exception
     * messages.
     */
    private void logSnapshotFailure(DeviceContext context, RuntimeException failure) {
        var causes = ExceptionUtils.getThrowableList(failure);
        // A SQLException may wrap an I/O failure, so inspect the whole cause chain.
        var sqlFailure = causes.stream().filter(SQLException.class::isInstance)
                .map(SQLException.class::cast).findFirst().orElse(null);
        var sqlState = sqlFailure == null ? null : sqlFailure.getSQLState();
        // Only standard SQLState codes are safe to include as driver-provided text.
        if (sqlState == null || !sqlState.matches("[0-9A-Z]{5}"))
            sqlState = "unknown";
        var errorCode = sqlFailure == null ? "unknown" : Integer.toString(sqlFailure.getErrorCode());
        log.warn(
                "RSU forwarding snapshot replacement failed [ip={} rsuId={} error={} cause={} sqlState={} errorCode={}]",
                context.device().target().address(), context.rsuId(),
                failure.getClass().getSimpleName(), causes.getLast().getClass().getSimpleName(), sqlState, errorCode);
    }

    private Map<String, MessageForwardingResult> awaitResults(Map<String, SnmpTaskHandle> handles) {
        // Submit the entire batch before waiting, so different devices can progress
        // concurrently.
        var results = new LinkedHashMap<String, MessageForwardingResult>();
        try {
            for (var entry : handles.entrySet())
                results.put(entry.getKey(), buildResponse(entry.getValue().await()));
        } catch (InterruptedException e) {
            // Cancellation cannot undo a dispatched SET. Return current evidence, including
            // RUNNING work, rather than claiming every worker has stopped or every device
            // is unchanged.
            handles.values().forEach(SnmpTaskHandle::cancel);
            handles.forEach((ip, handle) -> results.put(ip, buildResponse(handle.snapshot())));
            Thread.currentThread().interrupt();
        }
        return results;
    }

    /**
     * Builds the response with both the RSU outcome and the database update status.
     *
     * {@code changed} is true when a create/delete was verified, false when
     * deletion found no matching row, and null when the forwarding operation did
     * not finish. A later database failure does not erase a verified RSU change:
     * for example, the response can report {@code changed=true} and
     * {@code databaseSync=FAILED}.
     *
     * Detailed SNMP command values are omitted from this response.
     */
    private MessageForwardingResult buildResponse(SnmpTaskResult result) {
        Boolean changed = Ntcip1218ForwardingTasks.changed(result);
        var sync = result.steps().stream().filter(s -> s.name().equals("sync-database")).findFirst().orElseThrow();
        var databaseSync = switch (sync.status()) {
            case SUCCEEDED -> DatabaseSync.UPDATED;
            case NOT_STARTED -> DatabaseSync.NOT_UPDATED;
            case RUNNING -> DatabaseSync.IN_PROGRESS;
            default -> DatabaseSync.FAILED;
        };
        return new MessageForwardingResult(result.summary(), result.writeState(), changed, databaseSync);
    }
}
