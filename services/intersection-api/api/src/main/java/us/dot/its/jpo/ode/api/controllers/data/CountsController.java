package us.dot.its.jpo.ode.api.controllers.data;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import us.dot.its.jpo.ode.api.accessors.counts.CountsRepository;
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

    @Autowired
    public CountsController(CountsRepository countsRepository) {
        this.countsRepository = countsRepository;
    }

    @Operation(summary = "Get message counts for RSU", description = "Returns message counts for a specific RSU over a provided timespan")
    @RequestMapping(value = "/rsu", method = RequestMethod.GET, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('USER')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or USER role"),
    })
    public ResponseEntity<List<MessageCount>> getRsuMessageCounts(
            @RequestParam(name = "rsu_ip", required = true) String rsuIp,
            @RequestParam(name = "start_time_utc_millis", required = true) Long startTime,
            @RequestParam(name = "end_time_utc_millis", required = true) Long endTime) {

        log.debug("Getting message counts for RSU {} from {} to {}", rsuIp, startTime, endTime);

        List<MessageCount> counts = countsRepository.getMessageCounts(rsuIp, startTime, endTime);

        log.debug("Found {} message counts for RSU {}", counts.size(), rsuIp);
        return ResponseEntity.ok(counts);
    }

}
