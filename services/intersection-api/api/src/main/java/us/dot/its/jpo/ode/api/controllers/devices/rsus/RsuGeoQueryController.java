package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.devices.RsuGeoQueryRequest;
import us.dot.its.jpo.ode.api.services.RsuGeoQueryService;

/**
 * {@code POST /devices/rsus/geo-query} returns IPv4 addresses of RSUs in an organization
 * whose geography lies inside the request polygon.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@ApiResponses(value = {
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
})
@RequestMapping("/devices/rsus/geo-query")
public class RsuGeoQueryController {
    private final RsuGeoQueryService rsuGeoQueryService;

    /**
     * @param organization organization whose RSUs are searched
     * @param body polygon ring and optional manufacturer name
     * @return host addresses of RSUs inside the polygon
     */
    @Operation(summary = "Query RSU IPs inside a polygon", description = "Returns IPv4 addresses of RSUs in the organization whose geography lies inside the supplied polygon. "
            + "A vendor of \"Select Vendor\", blank, or omitted applies no manufacturer filter.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Geometry is missing or a point does not contain longitude and latitude"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or USER role in the organization")
    })
    @PostMapping(produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRoleInOrg(#organization, 'USER')")
    public ResponseEntity<List<String>> queryRsusByGeometry(
            @RequestHeader(name = "Organization") String organization,
            @Validated @RequestBody RsuGeoQueryRequest body) {
        log.debug("POST /devices/rsus/geo-query. organization: {}", organization);
        return ResponseEntity.ok(rsuGeoQueryService.findRsuIps(organization, body.getGeometry(), body.getVendor()));
    }
}
