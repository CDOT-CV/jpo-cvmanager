package us.dot.its.jpo.ode.api.controllers.users;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import us.dot.its.jpo.ode.api.models.emails.EmailSendResponse;
import us.dot.its.jpo.ode.api.models.emails.contents.access_requests.AccessRequestEmailContents;
import us.dot.its.jpo.ode.api.services.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@ApiResponses(value = {
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
})
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final EmailService emailService;

    @Operation(summary = "Request Organization Access", description = "Request access to an organization")
    @RequestMapping(value = "/request-organization-access", method = RequestMethod.POST, produces = "application/json")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid message body"),
    })
    public @ResponseBody ResponseEntity<String> decode_request(
            @RequestBody AccessRequestEmailContents body) {

        return EmailSendResponse.getCombinedResponseEntity(emailService.sendAccessRequest(body));
    }
}