package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.rsu.MsgFwdConfigsResponse;
import us.dot.its.jpo.ode.api.services.RsuMsgFwdQueryService;

@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@RequestMapping("/devices/rsus/msgfwd-query")
public class RsuMsgFwdQueryController {
    private final RsuMsgFwdQueryService rsuMsgFwdQueryService;

    @Operation(summary = "Get message forwarding configurations stored in the database for one RSU")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid IP, or missing rsu_ip parameter or Organization header"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping(produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || "
            + "(@PermissionService.hasRoleInOrg(#organization, 'USER') and @PermissionService.hasRsu(#rsuIp, 'USER'))")
    public MsgFwdConfigsResponse getMsgFwdConfigs(
            @RequestHeader(name = "Organization") String organization,
            @RequestParam(name = "rsu_ip") String rsuIp) {
        log.debug("GET /devices/rsus/msgfwd-query. organization: {}", organization);
        return rsuMsgFwdQueryService.getMsgFwdConfigs(rsuIp, organization);
    }
}
