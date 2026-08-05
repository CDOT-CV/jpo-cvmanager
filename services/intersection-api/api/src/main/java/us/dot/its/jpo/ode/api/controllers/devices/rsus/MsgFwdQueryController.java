package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import us.dot.its.jpo.ode.api.models.postgres.dtos.RsuMsgFwdConfigsDto;
import us.dot.its.jpo.ode.api.services.RsuMsgFwdQueryService;

@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@ApiResponses(value = {
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
})
@RequestMapping("/devices/rsus/msgfwd-query")
@RequiredArgsConstructor
public class MsgFwdQueryController {

    private final RsuMsgFwdQueryService rsuMsgFwdQueryService;

    @Operation(summary = "Get RSU Message Forwarding Configurations", description = "Returns the SNMP message forwarding configurations stored in the database for the given RSU.")
    @RequestMapping(method = RequestMethod.GET, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || (@PermissionService.hasRsu(#rsuIp, 'USER') and @PermissionService.hasRole('USER'))")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = RsuMsgFwdConfigsDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires membership in the specified organisation and access to the requested RSU"),
    })
    public ResponseEntity<RsuMsgFwdConfigsDto> getMsgFwdConfigs(
            @RequestParam(name = "rsu_ip", required = true) String rsuIp,
            @RequestHeader(name = "Organization", required = true) String organization) {
        log.debug("GET /devices/rsus/msgfwd-query requested for RSU '{}' in organisation '{}'", rsuIp, organization);
        RsuMsgFwdConfigsDto response = rsuMsgFwdQueryService.getMsgFwdConfigs(rsuIp, organization);
        return ResponseEntity.ok(response);
    }
}
