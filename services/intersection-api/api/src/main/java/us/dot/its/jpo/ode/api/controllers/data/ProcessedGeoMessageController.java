package us.dot.its.jpo.ode.api.controllers.data;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import us.dot.its.jpo.ode.api.models.geo.ProcessedGeoMessageRequest;
import us.dot.its.jpo.ode.api.services.ProcessedGeoMessageService;

/** CV Manager polygon query over processed BSM and PSM messages. */
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@RequestMapping("/rsu-geo-msg-data")
public class ProcessedGeoMessageController {

    private final ProcessedGeoMessageService service;
    private final boolean enabled;

    public ProcessedGeoMessageController(
            ProcessedGeoMessageService service,
            @Value("${geo-query.enabled:true}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Operation(summary = "Find processed BSM or PSM data within a polygon")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Matching GeoJSON features"),
            @ApiResponse(responseCode = "400", description = "Invalid polygon, time range, or message type"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Requires SUPER_USER or USER role"),
            @ApiResponse(responseCode = "501", description = "RSU feature is disabled"),
            @ApiResponse(responseCode = "503", description = "MongoDB is unavailable"),
            @ApiResponse(responseCode = "500", description = "Unexpected processing error")
    })
    @PostMapping(produces = "application/json", consumes = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('USER')")
    public List<Map<String, Object>> query(@RequestBody ProcessedGeoMessageRequest request) {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "RSU geo message queries are disabled.");
        }
        return service.query(request);
    }
}
