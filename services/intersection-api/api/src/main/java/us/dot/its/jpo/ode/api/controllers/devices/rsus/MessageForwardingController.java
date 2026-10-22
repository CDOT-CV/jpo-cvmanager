package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import us.dot.its.jpo.ode.api.models.devices.management.MessageForwardingConfiguration;
import us.dot.its.jpo.ode.api.services.RsuMessageForwardingReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import us.dot.its.jpo.ode.api.models.devices.management.CreateMessageForwardingRequest;
import us.dot.its.jpo.ode.api.models.devices.management.DeleteMessageForwardingRowRequest;
import us.dot.its.jpo.snmp.rsu.ntcip1218.ForwardingTable;
import us.dot.its.jpo.ode.api.models.devices.management.MessageForwardingRequest;
import us.dot.its.jpo.ode.api.models.devices.management.MessageForwardingResult;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.RsuMessageForwardingService;

/**
 * Forwarding reads require USER access and ownership; creation/deletion
 * requires OPERATOR access and ownership. Shared PermissionService helpers
 * apply the selected organization and currently define ownership through the
 * RSU's SSH credential. Keeping that rule in the helpers lets a future RSU
 * ownership model replace it without changing this controller.
 * 
 * Batch change responses report per-device outcomes; HTTP 200 alone does not
 * imply success.
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enable.api", havingValue = "true")
@RequestMapping("/devices/rsus/message-forwarding")
public class MessageForwardingController {
    private final RsuMessageForwardingService forwardingService;
    private final PermissionService permissionService;
    private final RsuMessageForwardingReadService readService;

    @PostMapping(produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('OPERATOR')")
    @Operation(summary = "Create RSU message forwarding", description = "Creates and verifies an NTCIP 1218 UDP forwarding row on each RSU. Message type selects the destination port. Results are per RSU; an uncertain write must be reconciled before retrying.")
    public Map<String, MessageForwardingResult> create(@Valid @RequestBody CreateMessageForwardingRequest body) {
        authorize(body);
        return forwardingService.create(body);
    }

    /**
     * Used by bulk commands when callers know the message type and destination,
     * but have not read the row indexes from each RSU. The API selects matching rows.
     */
    @DeleteMapping(produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('OPERATOR')")
    @Operation(summary = "Delete RSU message forwarding", description = "Deletes the highest matching message-type/destination row on each RSU. No matching row is a successful no-op.")
    public Map<String, MessageForwardingResult> delete(@Valid @RequestBody MessageForwardingRequest body) {
        authorize(body);
        return forwardingService.delete(body);
    }

    /**
     * Used by the forwarding list to delete the displayed row by its table/index.
     * Callers pass the returned identity rather than infer a table from the message type.
     */
    @DeleteMapping(path = "/{rsuIp}/rows/{table}/{index}", produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('OPERATOR')")
    @Operation(summary = "Delete a specific forwarding row", description = "Deletes the selected NTCIP 1218 table/index, including unknown PSIDs and message types stored in a different table than CV Manager uses. Checks the displayed destination and port before writing, then verifies absence and synchronizes the database. Creating configurations still uses the fixed message presets.")
    public Map<String, MessageForwardingResult> deleteRow(@PathVariable String rsuIp,
            @PathVariable String table, @PathVariable String index,
            @Valid @RequestBody DeleteMessageForwardingRowRequest body) {
        ForwardingTable selectedTable;
        int selectedIndex;
        try {
            selectedTable = ForwardingTable.valueOf(table);
            selectedIndex = Integer.parseInt(index);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected a RECEIVE or TRANSMIT table and index 1-255");
        }
        RsuMessageForwardingService.validateRowRequest(rsuIp, selectedTable, selectedIndex, body);
        if (!permissionService.findUnauthorizedRsus(List.of(rsuIp), "OPERATOR").isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization does not own the requested RSU");
        }
        return forwardingService.deleteRow(rsuIp, selectedTable, selectedIndex, body);
    }

    @GetMapping(path = "/{rsuIp}", produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('USER')")
    @Operation(summary = "Get cached RSU message forwarding", description = "Returns PostgreSQL forwarding rows without contacting the RSU or requiring SNMP credentials.")
    public List<MessageForwardingConfiguration> cached(@PathVariable String rsuIp) {
        authorizeRead(rsuIp);
        return readService.cached(rsuIp);
    }

    @GetMapping(path = "/{rsuIp}/live", produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('USER')")
    @Operation(summary = "Read forwarding directly from an RSU", description = "Reads both NTCIP 1218 forwarding tables using bounded SNMP execution. Does not update PostgreSQL. A failed read returns an error rather than a partial snapshot.")
    public List<MessageForwardingConfiguration> live(@PathVariable String rsuIp) {
        authorizeRead(rsuIp);
        return readService.live(rsuIp);
    }

    private void authorizeRead(String rsuIp) {
        RsuMessageForwardingReadService.validateAddress(rsuIp);
        if (!permissionService.findUnauthorizedRsus(List.of(rsuIp), "USER").isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization does not own the requested RSU");
        }
    }

    private void authorize(MessageForwardingRequest body) {
        // Validate IPv4 addresses before checking permissions.
        RsuMessageForwardingService.validateRequest(body);
        if (!permissionService.findUnauthorizedRsus(body.getRsuIps(), "OPERATOR").isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Organization does not own all requested RSUs. No forwarding commands sent.");
        }
    }
}
