package us.dot.its.jpo.ode.api.controllers.scms;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import us.dot.its.jpo.ode.api.mappers.ScmsHealthMapper;
import us.dot.its.jpo.ode.api.models.scms.ScmsHealthDto;
import us.dot.its.jpo.ode.api.services.ScmsHealthService;
import java.util.Map;

@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true")
@RequestMapping("/scms-status")
@RequiredArgsConstructor
@Tag(name = "SCMS Health Status", description = "Retrieves the SCMS health status for RSUs in the given organization")
public class ScmsHealthController {

    private final ScmsHealthService scmsHealthService;
    private final ScmsHealthMapper scmsHealthMapper;

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
            @ApiResponse(responseCode = "500", description = "Organization header is missing"),
    })
    @GetMapping(produces = "application/json")
    public Map<String, ScmsHealthDto> getAllStatuses(@RequestHeader(name = "Organization") String organization) {
        log.info("GET /scms-status. organization: {}", organization);
        return scmsHealthMapper.toMap(scmsHealthService.getScmsStatuses(organization));
    }
}
