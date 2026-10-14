package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Operation(summary = "Get RSU message forwarding configurations",
            description = "Returns the SNMP message forwarding configurations stored in the database for the given RSU "
                    + "within the requested organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success",
                    content = @Content(schema = @Schema(implementation = MsgFwdConfigsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid rsu_ip"),
            @ApiResponse(responseCode = "403",
                    description = "Organization header is missing, or the user lacks the USER role in the organization or access to the RSU")
    })
    @GetMapping(produces = "application/json")
    @PreAuthorize("#organization != null && (@PermissionService.isSuperUser() || "
            + "(@PermissionService.hasRsu(#rsuIp, 'USER') and @PermissionService.hasRole('USER')))")
    public MsgFwdConfigsResponse getMsgFwdConfigs(
            @RequestParam(name = "rsu_ip") String rsuIp,
            @RequestHeader(name = "Organization", required = false) String organization) {
        log.info("GET /devices/rsus/msgfwd-query. rsu_ip: {}, organization: {}", rsuIp, organization);
        return rsuMsgFwdQueryService.getMsgFwdConfigs(rsuIp, organization);
    }
}
