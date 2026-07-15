package us.dot.its.jpo.ode.api.controllers;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.services.WzdxService;

@Slf4j
@RestController
@ConditionalOnProperty(name = { "enable.api", "enable.wzdx-feed" }, havingValue = "true", matchIfMissing = false)
@Tag(name = "WZDx", description = "Endpoint for retrieving the WZDx work-zone data feed.")
@ApiResponses(value = {
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
})
@RequestMapping("/wzdx-feed")
@RequiredArgsConstructor
public class WzdxFeedController {

    private final WzdxService wzdxService;

    @Operation(summary = "WZDx Feed", description = "Retrieves the WZDx work-zone data feed from the configured upstream provider.")
    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "WZDx feed retrieved successfully"),
            @ApiResponse(responseCode = "502", description = "Failed to retrieve the upstream WZDx feed")
    })
    public ResponseEntity<String> getWzdxFeed() {
        log.debug("WzdxFeed GET requested");
        return ResponseEntity.ok(wzdxService.callWzdxApi());
    }
}
