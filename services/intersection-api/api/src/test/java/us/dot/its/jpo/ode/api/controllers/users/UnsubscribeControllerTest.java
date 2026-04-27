package us.dot.its.jpo.ode.api.controllers.users;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import javax.ws.rs.NotAuthorizedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.UserEmailNotificationDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.Role;
import us.dot.its.jpo.ode.api.models.postgres.tables.UserOrganization;
import us.dot.its.jpo.ode.api.repositories.UserOrganizationRepository;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.emails.EmailSubscriptionGetResponse;
import us.dot.its.jpo.ode.api.services.EmailService;
import us.dot.its.jpo.ode.api.services.PermissionService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class UnsubscribeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private PermissionService permissionService;

    @MockitoBean
    private UnsubscribeTokenGenerator tokenGenerator;

    @MockitoBean
    private UserOrganizationRepository userOrganizationRepository;

    private static final String validToken = "valid-token-123";
    private static final String invalidToken = "invalid-token-456";
    private static final String email = "test@example.com";

    private static final List<UserEmailNotificationDto> validSubscriptionList = Arrays.asList(
            new UserEmailNotificationDto("Support Requests", "Receive support requests from users", "admin", true,
                    false, false, false, false,
                    true, false, false, false, false),
            new UserEmailNotificationDto("Firmware Upgrade Failures",
                    "Receive automated firmware upgrade failure emails",
                    "operator", true, false, false, false, false,
                    true, false, false, false, false));

    @Nested
    @DisplayName("GET /users/subscriptions/email-subscriptions — list all subscriptions")
    class GetAllSubscriptions {

        @Test
        @DisplayName("returns 403 when no permissions are granted (unauthenticated)")
        void noPermissions_returns403() throws Exception {
            // Spring Security filter runs before argument binding; unauthenticated → 403
            mockMvc.perform(get("/users/unsubscribe/email-subscriptions"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 403 when authenticated but neither isSuperUser nor hasRole('USER')")
        void authenticated_invalidToken_returns403() throws Exception {
        when(tokenGenerator.parseAndValidateToken(invalidToken)).thenReturn(null);

            mockMvc.perform(get("/users/unsubscribe/email-subscriptions"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 403 when authenticated but neither isSuperUser nor hasRole('USER')")
        void authenticated_validToken_200() throws Exception {

        Role roleOperator = mock(Role.class);
        when(roleOperator.getName()).thenReturn("operator");
        UserOrganization orgOperator = mock(UserOrganization.class);
        when(orgOperator.getRole()).thenReturn(roleOperator);

        Role roleAdmin = mock(Role.class);
        when(roleAdmin.getName()).thenReturn("admin");
        UserOrganization orgAdmin = mock(UserOrganization.class);
        when(orgAdmin.getRole()).thenReturn(roleAdmin);

        List<UserOrganization> authToken = Arrays.asList(orgAdmin, orgOperator);
        when(userOrganizationRepository.findAllByEmail(email)).thenReturn(authToken);

        when(tokenGenerator.parseAndValidateToken(validToken)).thenReturn(email);
        when(emailService.getAllEmailSubscriptionOptionsForUser(email, true, true)).thenReturn(validSubscriptionList);

            mockMvc.perform(get("/users/unsubscribe/email-subscriptions"))
                    .andExpect(status().isForbidden());
        }
}
