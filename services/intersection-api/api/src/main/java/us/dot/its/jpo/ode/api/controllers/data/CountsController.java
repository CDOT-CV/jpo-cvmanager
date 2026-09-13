package us.dot.its.jpo.ode.api.controllers.data;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import us.dot.its.jpo.ode.api.accessors.counts.CountsRepository;
import us.dot.its.jpo.ode.api.accessors.counts.MessageTypeParams;
import us.dot.its.jpo.ode.api.models.MessageCount;

@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@ApiResponses(value = {
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
})
@RequestMapping("/data/counts")
public class CountsController {

    private final CountsRepository countsRepository;

    public CountsController(CountsRepository countsRepository) {
        this.countsRepository = countsRepository;
    }

    @Operation(summary = "Get message counts for RSU", description = "Returns message counts for a specific RSU over a provided timespan. "
            + "Supply one or more message types via repeated `message` parameters or a comma-separated list.")
    @RequestMapping(value = "/rsus/{rsu_ip}", method = RequestMethod.GET, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRsu(#rsuIp, 'USER')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or USER role with access to the requested RSU"),
    })
    public ResponseEntity<List<MessageCount>> getRsuMessageCounts(
            @PathVariable(name = "rsu_ip") String rsuIp,
            @Parameter(description = "Message types to include. Repeat the parameter or provide a comma-separated list (e.g. BSM,MAP,SPAT).") @RequestParam(name = "message", required = false) List<String> message,
            @RequestParam(name = "start_time_utc_millis", required = true) Long startTime,
            @RequestParam(name = "end_time_utc_millis", required = true) Long endTime) {

        List<String> messageTypes = MessageTypeParams.parse(message);
        log.debug("Getting message counts for RSU {} message types {} from {} to {}", rsuIp, messageTypes, startTime,
                endTime);

        List<MessageCount> counts = countsRepository.getRsuMessageCounts(rsuIp, messageTypes, startTime, endTime);

        log.debug("Found {} message counts for RSU {} message types {}", counts.size(), rsuIp, messageTypes);
        return ResponseEntity.ok(counts);
    }

    @Operation(summary = "Get organization RSU message counts", description = "Returns message counts for all RSUs in an organization over a provided timespan")
    @RequestMapping(value = "/rsus/organizations/{organization}", method = RequestMethod.GET, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRoleInOrg(#organization, 'USER')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or USER role in the requested organization"),
    })
    public ResponseEntity<List<MessageCount>> getOrganizationRsuMessageCounts(
            @PathVariable(name = "organization") String organization,
            @RequestParam(name = "message", required = true) String message,
            @RequestParam(name = "start_time_utc_millis", required = true) Long startTime,
            @RequestParam(name = "end_time_utc_millis", required = true) Long endTime) {

        log.debug("Getting organization RSU message counts for {} from {} to {}",
                organization, startTime, endTime);

        List<MessageCount> counts = countsRepository.getRsuOrganizationMessageCounts(organization, message,
                startTime,
                endTime);

        log.debug("Found {} message counts for organization {}", counts.size(), organization);
        return ResponseEntity.ok(counts);
    }

}
