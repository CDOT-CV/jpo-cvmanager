package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import us.dot.its.jpo.ode.api.services.RsuManagementService;
import us.dot.its.jpo.ode.api.services.RsuRebootService;

/** Accepts Commsignia RSU reboots using the existing SSH credential ownership rule. */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enable.api", havingValue = "true")
@RequestMapping("/devices/rsus/reboot")
public class RebootController {
    private final RsuRebootService service;

    @PostMapping(path = "/{rsuIp}", produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || (@PermissionService.hasRole('OPERATOR')"
            + " && @PermissionService.isRsuOwner(#rsuIp, 'OPERATOR'))")
    @Operation(summary = "Request a Commsignia RSU reboot", description = "Supports Commsignia devices only; other manufacturers are rejected before admission. Requires OPERATOR access and ownership. Returns 202 after admission, without waiting for SSH or device restart. Final command outcomes are logged using requestId; requests are not retried or persisted across API restarts.")
    public ResponseEntity<RebootAccepted> reboot(@PathVariable String rsuIp) {
        var address = RsuManagementService.toIpv4Address(rsuIp);

        return ResponseEntity.accepted().body(new RebootAccepted(service.requestReboot(address), "ACCEPTED"));
    }

    /** Identifies an accepted background request, not a completed reboot. */
    public record RebootAccepted(UUID requestId, String status) { }
}
