package us.dot.its.jpo.ode.api.controllers.users;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailSubscriptionGetResponse;
import us.dot.its.jpo.ode.api.models.postgres.derived.EmailSubscription;
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
@RequestMapping("/users/unsubscribe")
@RequiredArgsConstructor
public class UnsubscribeController {
    private final EmailService emailService;
    private final UnsubscribeTokenGenerator unsubscribeTokenGenerator;

    @Operation(summary = "Update email subscription preferences", description = "Update the user's email subscription preferences")
    @RequestMapping(value = "/email-subscriptions", method = RequestMethod.POST, produces = "application/json")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid message body"),
    })
    public @ResponseBody ResponseEntity<String> updateEmailSubscriptions(
            @RequestParam(required = false) String token,
            @RequestBody List<EmailSubscription> requestedSubscriptions) {
        String userEmail = unsubscribeTokenGenerator.parseAndValidateToken(token);
        if (userEmail == null) {
            return ResponseEntity.status(401).build();
        }

        emailService.updateEmailSubscriptions(userEmail, requestedSubscriptions);

        return ResponseEntity.ok("Email subscriptions updated successfully");
    }

    @RequestMapping(value = "/email-subscriptions", method = RequestMethod.GET, produces = "application/json")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid message body"),
    })
    public @ResponseBody ResponseEntity<EmailSubscriptionGetResponse> getEmailSubscriptions(
            @RequestParam(required = false) String token) {
        String userEmail = unsubscribeTokenGenerator.parseAndValidateToken(token);
        if (userEmail == null) {
            return ResponseEntity.status(401).build();
        }
        List<EmailSubscription> subscriptions = emailService.getAllEmailSubscriptionOptionsForUser(userEmail);
        return ResponseEntity.ok(new EmailSubscriptionGetResponse(subscriptions, userEmail));
    }
}