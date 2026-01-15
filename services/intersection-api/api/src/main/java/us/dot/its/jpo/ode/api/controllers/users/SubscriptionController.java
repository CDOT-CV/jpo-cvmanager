package us.dot.its.jpo.ode.api.controllers.users;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import us.dot.its.jpo.ode.api.models.emails.EmailSubscriptionGetResponse;
import us.dot.its.jpo.ode.api.models.postgres.derived.EmailSubscription;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.PostgresService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@ApiResponses(value = {
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
})
@RequestMapping("/users/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {
    private final PostgresService postgresService;

    @Operation(summary = "Update email subscription preferences", description = "Update the user's email subscription preferences")
    @RequestMapping(value = "/email-subscriptions", method = RequestMethod.POST, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('USER')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid message body"),
    })
    public @ResponseBody ResponseEntity<String> updateEmailSubscriptions(
            @RequestBody List<EmailSubscription> requestedSubscriptions) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = PermissionService.getUsername(auth);

        List<EmailSubscription> userSubscriptions = postgresService.getEmailSubscriptionsByUser(userEmail);
        List<String> addedSubscriptions = requestedSubscriptions.stream()
                .filter(sub -> sub.getSubscribed())
                .filter(sub -> userSubscriptions.stream()
                        .noneMatch(userSub -> userSub.getCategory().equals(sub.getCategory())))
                .map(EmailSubscription::getCategory)
                .toList();

        List<EmailSubscription> modifiedSubscriptions = requestedSubscriptions.stream()
                .filter(sub -> sub.getSubscribed() != null && sub.getSubscribed())
                .filter(sub -> userSubscriptions.stream()
                        .anyMatch(userSub -> userSub.isFrequencyEqual(sub)))
                .toList();

        List<String> removedSubscriptions = requestedSubscriptions.stream()
                .filter(sub -> !sub.getSubscribed())
                .filter(sub -> userSubscriptions.stream()
                        .anyMatch(userSub -> userSub.getCategory().equals(sub.getCategory())))
                .map(EmailSubscription::getCategory)
                .toList();

        if (!removedSubscriptions.isEmpty()) {
            postgresService.removeEmailSubscriptionsByUser(userEmail, removedSubscriptions);
        }
        modifiedSubscriptions.forEach(subType -> {
            postgresService.updateEmailSubscriptionByUser(userEmail, subType);
        });
        addedSubscriptions.forEach(subType -> {
            postgresService.addEmailSubscriptionByUser(userEmail, subType);
        });

        return ResponseEntity.ok("Email subscriptions updated successfully");
    }

    @RequestMapping(value = "/email-subscriptions", method = RequestMethod.GET, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('USER')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid message body"),
    })
    public @ResponseBody ResponseEntity<EmailSubscriptionGetResponse> getEmailSubscriptions() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = PermissionService.getUsername(auth);

        List<EmailSubscription> userSubscriptions = postgresService.getEmailSubscriptionsByUser(userEmail);
        List<EmailSubscription> allSubscriptionTypes = postgresService.getEmailSubscriptionTypes();
        List<EmailSubscription> subscriptions = allSubscriptionTypes.stream().map(subType -> {
            for (EmailSubscription subscribedType : userSubscriptions) {
                if (subscribedType.getCategory().equals(subType.getCategory())) {
                    return subscribedType;
                }
            }
            return subType;
        }).toList();
        return ResponseEntity.ok(new EmailSubscriptionGetResponse(subscriptions, userEmail));
    }
}