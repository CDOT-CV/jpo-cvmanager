package us.dot.its.jpo.ode.api.controllers.devices.management;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import us.dot.its.jpo.ode.api.models.devices.management.GetModifyRsuData;
import us.dot.its.jpo.ode.api.models.devices.management.GetModifyRsuDataSingle;
import us.dot.its.jpo.ode.api.models.devices.management.ModifyRsuAllowedSelections;
import us.dot.its.jpo.ode.api.models.postgres.derived.RsuDetailedInfo;
import us.dot.its.jpo.ode.api.models.postgres.derived.RsuDetailedInfoRow;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.RsuManagementService;
import us.dot.its.jpo.ode.api.utils.RsuAggregationUtil;

@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@ApiResponses(value = {
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
})
@RequestMapping("/devices/management/rsu")
@RequiredArgsConstructor
public class RsuManagementController {
    private final RsuRepository rsuRepository;
    private final RsuManagementService rsuManagementService;

    @Operation(summary = "Get All RSUs for Organization", description = "Get summary data for all RSUs the user has access to in the specified organization.")
    @RequestMapping(method = RequestMethod.GET, produces = "application/json", params = "!rsu_ip")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('USER')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or USER role"),
    })
    public GetModifyRsuData getAllRsus(
            @RequestHeader(name = "Organization", required = true) String organization) {
        log.info("Getting all RSUs for organization: {}", organization);
        // TODO: Support paging for large result sets
        List<RsuDetailedInfoRow> allRsus = rsuRepository.findAllDetailedRsuInfoRowsByOrganization(organization);
        List<RsuDetailedInfo> rsuInfoList = RsuAggregationUtil.aggregateRsuRowsToList(allRsus);
        return new GetModifyRsuData(rsuInfoList);
    }

    @Operation(summary = "Get Single RSU Management Data", description = "Get RSU data required for RSU modification page. "
            + "Returns detailed data for the specified RSU along with allowed selections for modification.")
    @RequestMapping(method = RequestMethod.GET, produces = "application/json", params = "rsu_ip")
    @PreAuthorize("@PermissionService.isSuperUser() || (@PermissionService.hasRsu(#rsuIp, 'USER') and @PermissionService.hasRole('USER'))")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or USER role with access to the RSU requested"),
    })
    public GetModifyRsuDataSingle getSingleRsuData(
            @RequestHeader(name = "Organization", required = true) String organization,
            @RequestParam(name = "rsu_ip", required = true) String rsuIp) {
        log.info("Getting RSU data for IP: {} in organization: {}", rsuIp, organization);
        List<RsuDetailedInfoRow> allRsuInfo = rsuRepository.findDetailedRsuInfoRowsByIp(rsuIp);
        List<RsuDetailedInfo> rsuInfoList = RsuAggregationUtil.aggregateRsuRowsToList(allRsuInfo);
        if (rsuInfoList.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RSU not found");
        } else if (rsuInfoList.size() > 1) {
            log.error("Multiple RSUs found with the same IP address: {}", rsuIp);
            throw new ResponseStatusException(HttpStatusCode.valueOf(500),
                    "Multiple RSUs found with the same IP address");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = PermissionService.getUsername(auth);
        ModifyRsuAllowedSelections allowedSelections = rsuManagementService.getAllowedSelections(username);

        return new GetModifyRsuDataSingle(rsuInfoList.getFirst(), allowedSelections);
    }
}