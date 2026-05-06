package us.dot.its.jpo.ode.api.controllers.users;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.UserEmailNotificationDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.UserOrganization;
import us.dot.its.jpo.ode.api.repositories.UserOrganizationRepository;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.emails.EmailSubscriptionGetResponse;
import us.dot.its.jpo.ode.api.services.EmailService;

import org.springframework.web.bind.annotation.RequestBody;

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
    private final UserOrganizationRepository userOrganizationRepository;
    private final UnsubscribeTokenGenerator unsubscribeTokenGenerator;

    @RequestMapping(value = "/email-subscriptions", method = RequestMethod.GET, produces = "application/json")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid message body"),
    })
    public EmailSubscriptionGetResponse getEmailSubscriptions(
            @RequestParam() String token) {
        String userEmail = unsubscribeTokenGenerator.parseAndValidateToken(token);
        if (userEmail == null) {
            throw new AccessDeniedException("Invalid or expired token");
        }

        List<UserOrganization> userOrganizations = userOrganizationRepository.findAllByEmail(userEmail);
        boolean isOperator = userOrganizations.stream()
                .anyMatch(org -> UserRole.OPERATOR.equals(UserRole.fromString(org.getRole().getName())));
        boolean isAdmin = userOrganizations.stream()
                .anyMatch(org -> UserRole.ADMIN.equals(UserRole.fromString(org.getRole().getName())));

        List<UserEmailNotificationDto> subscriptions = emailService.getAllEmailSubscriptionOptionsForUser(userEmail,
                isOperator, isAdmin);
        return new EmailSubscriptionGetResponse(subscriptions, userEmail);
    }

    @Operation(summary = "Update email subscription preferences", description = "Update the user's email subscription preferences")
    @RequestMapping(value = "/email-subscriptions", method = RequestMethod.POST, produces = "application/json")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid message body"),
    })
    public void updateEmailSubscriptions(
            @RequestParam() String token,
            @Valid @RequestBody List<UserEmailNotificationDto> requestedSubscriptions) {
        String userEmail = unsubscribeTokenGenerator.parseAndValidateToken(token);
        if (userEmail == null) {
            throw new AccessDeniedException("Invalid or expired token");
        }

        emailService.updateEmailSubscriptions(userEmail, requestedSubscriptions);

        return;
    }
}