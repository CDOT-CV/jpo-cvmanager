package us.dot.its.jpo.ode.api.controllers.users;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import us.dot.its.jpo.ode.api.models.emails.UserEmailNotificationDto;
import us.dot.its.jpo.ode.api.models.keycloak.CvManagerAuthToken;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.emails.EmailSubscriptionGetResponse;
import us.dot.its.jpo.ode.api.services.EmailService;
import us.dot.its.jpo.ode.api.services.PermissionService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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
    private final EmailService emailService;
    private final PermissionService permissionService;

    @GetMapping(value = "/email-subscriptions", produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('USER')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid message body"),
    })
    public EmailSubscriptionGetResponse getEmailSubscriptions() {

        CvManagerAuthToken authToken = permissionService.getCvManagerAuthToken();
        List<UserEmailNotificationDto> subscriptions = emailService.getAllEmailSubscriptionOptionsForUser(
                authToken.getEmail(), getOrgRoles(authToken), permissionService.isSuperUser());
        return new EmailSubscriptionGetResponse(subscriptions, authToken.getEmail());
    }

    @Operation(summary = "Update email subscription preferences", description = "Update the user's email subscription preferences")
    @PostMapping(value = "/email-subscriptions", produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('USER')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid message body"),
    })
    @ResponseStatus(HttpStatus.OK)
    public void updateEmailSubscriptions(
            @Valid @RequestBody List<UserEmailNotificationDto> requestedSubscriptions) {

        CvManagerAuthToken authToken = permissionService.getCvManagerAuthToken();
        emailService.updateEmailSubscriptions(authToken.getEmail(), getOrgRoles(authToken),
                permissionService.isSuperUser(), requestedSubscriptions);

        return;
    }

    /**
     * The user's role in each of their organizations, keyed by organization name.
     * Super users are treated as ADMIN in every organization they belong to.
     */
    private Map<String, UserRole> getOrgRoles(CvManagerAuthToken authToken) {
        boolean isSuperUser = permissionService.isSuperUser();
        return authToken.getOrgRoles().values().stream()
                .collect(Collectors.toMap(
                        orgRole -> orgRole.getLeft().getName(),
                        orgRole -> isSuperUser ? UserRole.ADMIN : orgRole.getRight(),
                        (first, second) -> first));
    }
}