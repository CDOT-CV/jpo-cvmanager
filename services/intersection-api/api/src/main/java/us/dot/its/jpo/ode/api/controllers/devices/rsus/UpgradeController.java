package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import us.dot.its.jpo.ode.api.mappers.FirmwareUpgradeMapper;
import us.dot.its.jpo.ode.api.models.devices.management.RsuSingleUpgradeCheckRequest;
import us.dot.its.jpo.ode.api.models.devices.management.RsuUpgradeRequest;
import us.dot.its.jpo.ode.api.models.postgres.dtos.FirmwareUpgradeCheckResponseDto;
import us.dot.its.jpo.ode.api.models.postgres.dtos.FirmwareUpgradeResultDto;
import us.dot.its.jpo.ode.api.services.RsuUpgradeService;

@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@ApiResponses(value = {
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
})
@RequestMapping("/devices/rsus/upgrade")
@RequiredArgsConstructor
public class UpgradeController {

    private final RsuUpgradeService rsuUpgradeService;
    private final FirmwareUpgradeMapper firmwareUpgradeMapper;

    @Operation(summary = "Start RSU Firmware Upgrade", description = "Marks the supplied RSUs for upgrade and triggers firmware manager processing.")
    @PostMapping(produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || (@PermissionService.hasRsus(#body.rsuIps, 'OPERATOR') and @PermissionService.hasRole('OPERATOR'))")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(description = "Map of RSU IPs to their upgrade results", example = "{\"192.168.1.1\": {\"code\": 200, \"data\": {\"message\": \"started\"}}, \"192.168.1.2\": {\"code\": 409, \"data\": \"already up to date\"}}"))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or OPERATOR role with access to all requested RSUs"),
    })
    public ResponseEntity<Map<String, FirmwareUpgradeResultDto>> startUpgrade(
            @RequestHeader(name = "Organization", required = true) String organization,
            @Validated @RequestBody RsuUpgradeRequest body) {
        Map<String, Object> response = rsuUpgradeService.startFirmwareUpgradeForRsus(organization, body.getRsuIps());
        Map<String, FirmwareUpgradeResultDto> mappedResponse = firmwareUpgradeMapper.mapStartUpgradeResponse(response);
        return ResponseEntity.ok(mappedResponse);
    }

    @Operation(summary = "Check RSU Firmware Upgrade Availability", description = "Checks whether a firmware upgrade is available for the requested RSU.")
    @PostMapping(path = "/check", produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || (@PermissionService.hasRsu(#body.rsuIp, 'OPERATOR') and @PermissionService.hasRole('OPERATOR'))")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = FirmwareUpgradeCheckResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or OPERATOR role with access to all requested RSUs"),
    })
    public ResponseEntity<FirmwareUpgradeCheckResponseDto> checkUpgrade(
            @RequestHeader(name = "Organization", required = true) String organization,
            @Validated @RequestBody RsuSingleUpgradeCheckRequest body) {
        Map<String, Object> response = rsuUpgradeService.checkFirmwareUpgrade(organization, body.getRsuIp());
        FirmwareUpgradeCheckResponseDto mappedResponse = firmwareUpgradeMapper.mapCheckUpgradeResponse(response);
        return ResponseEntity.ok(mappedResponse);
    }
}
