package us.dot.its.jpo.ode.api.controllers.admin;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import us.dot.its.jpo.ode.api.models.storage.ObuOtaRequestPage;
import us.dot.its.jpo.ode.api.services.ObuOtaRequestService;
import us.dot.its.jpo.ode.api.services.ObuOtaRequestService.Filters;

@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true")
@RequestMapping("/admin/obu-ota-requests")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin OBU OTA Requests", description = "View OBU firmware download history")
public class AdminObuOtaRequestController {
    private final ObuOtaRequestService obuOtaRequestService;

    @Operation(summary = "List OBU firmware download requests recorded by the OBU OTA server")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Requests returned"),
            @ApiResponse(responseCode = "400", description = "Invalid paging, filter, or sort value"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role")
    })
    @GetMapping(produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('ADMIN')")
    public ObuOtaRequestPage listRequests(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size,
            @RequestParam(name = "serial", required = false) String serial,
            @RequestParam(name = "manufacturer", required = false) String manufacturer,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(name = "end", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam(name = "sort", defaultValue = "request_datetime,desc") String sort) {
        return obuOtaRequestService.search(page, size, new Filters(serial, manufacturer, status, start, end), sort);
    }
}
