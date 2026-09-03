package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.rsu.LastOnlineDto;
import us.dot.its.jpo.ode.api.models.rsu.OnlineStatusResponse;
import us.dot.its.jpo.ode.api.services.RsuOnlineStatusService;

@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@RequestMapping("/devices/rsus/online-status")
public class RsuOnlineStatusController {
    private final RsuOnlineStatusService rsuOnlineStatusService;

    @Operation(summary = "Get online status for all RSUs in an organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Organization header is missing"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping(produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRoleInOrg(#organization, 'USER')")
    public OnlineStatusResponse getAllOnlineStatuses(
            @RequestHeader(name = "Organization") String organization) {
        log.debug("GET /devices/rsus/online-status. organization: {}", organization);
        return new OnlineStatusResponse(rsuOnlineStatusService.getOnlineStatuses(organization));
    }

    @Operation(summary = "Get last successful online time for one RSU")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid IP"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping(value = "/{ip}", produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRsu(#ip, 'USER')")
    public LastOnlineDto getLastOnline(@PathVariable String ip) {
        log.debug("GET /devices/rsus/online-status/{}", ip);
        return rsuOnlineStatusService.getLastOnline(ip);
    }
}
