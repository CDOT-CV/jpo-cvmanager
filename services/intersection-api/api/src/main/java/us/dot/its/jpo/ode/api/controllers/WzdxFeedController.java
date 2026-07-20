package us.dot.its.jpo.ode.api.controllers;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.geojsonconverter.DateJsonMapper;
import us.dot.its.jpo.ode.api.services.WzdxService;

@Slf4j
@RestController
@ConditionalOnProperty(name = { "enable.api", "enable.wzdx-feed" }, havingValue = "true")
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
    @PreAuthorize("isAuthenticated()")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "WZDx feed retrieved successfully"),
            @ApiResponse(responseCode = "502", description = "Failed to retrieve the upstream WZDx feed")
    })
    public ResponseEntity<JsonNode> getWzdxFeed() {
        log.debug("WzdxFeed GET requested");
        String feed = wzdxService.callWzdxApi();
        try {
            return ResponseEntity.ok(DateJsonMapper.getInstance().readTree(feed));
        } catch (JsonProcessingException e) {
            log.error("Upstream WZDx feed was not valid JSON", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream WZDx feed was not valid JSON", e);
        }
    }
}
