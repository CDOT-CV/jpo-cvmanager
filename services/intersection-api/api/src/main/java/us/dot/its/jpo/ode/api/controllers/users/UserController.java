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
import us.dot.its.jpo.ode.api.models.emails.ManageSubscriptionsBody;
import us.dot.its.jpo.ode.api.models.postgres.derived.EmailSubscription;
import us.dot.its.jpo.ode.api.models.postgres.tables.EmailType;
import us.dot.its.jpo.ode.api.services.PostgresService;

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
    private final PostgresService postgresService;
    private final UnsubscribeTokenGenerator unsubscribeTokenGenerator;

    // TODO: Remove all authentication for send-support-request-email
    @Operation(summary = "Manage email subscription preferences", description = "Manage the user's email subscription preferences")
    @RequestMapping(value = "/manage-email-subscriptions", method = RequestMethod.POST, produces = "application/json")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid message body"),
    })
    public @ResponseBody ResponseEntity<String> manageEmailSubscriptions(
            @RequestParam(name = "token", required = false) String token,
            @RequestBody EmailSubscription body) {
        String userEmail = unsubscribeTokenGenerator.parseAndValidateToken(token);
        if (userEmail == null) {
            return ResponseEntity.status(401).build();
        }
        return null;
    }

    @RequestMapping(value = "/email-subscriptions", method = RequestMethod.GET, produces = "application/json")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid message body"),
    })
    public @ResponseBody ResponseEntity<List<EmailSubscription>> getEmailSubscriptions(
            @RequestParam(name = "token", required = false) String token) {
        String userEmail = unsubscribeTokenGenerator.parseAndValidateToken(token);
        if (userEmail == null) {
            return ResponseEntity.status(401).build();
        }
        List<EmailType> userSubscriptions = postgresService.getEmailSubscriptionsByUser(userEmail);
        List<EmailSubscription> allSubscriptionTypes = postgresService.getEmailSubscriptionTypes();
        List<EmailSubscription> subscriptions = allSubscriptionTypes.stream().filter(subType -> {
            for (EmailType subscribedType : userSubscriptions) {
                if (subscribedType.getEmail_type().equals(subType.getCategory())) {
                    subType.setSubscribed(true);
                    return true;
                }
            }
            return false;
        }).toList();
        return ResponseEntity.ok(new EmailSubscriptionGetResponse(subscriptions, userEmail));
    }
}