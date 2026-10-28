package us.dot.its.jpo.ode.api.controllers.users;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.emails.UserEmailNotificationDto;
import us.dot.its.jpo.ode.api.models.keycloak.CvManagerAuthToken;
import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.services.EmailService;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private PermissionService permissionService;

    private CvManagerAuthToken authToken;

    private static final String email = "user@example.com";

    private static final List<UserEmailNotificationDto> validSubscriptionsList = Arrays.asList(
            new UserEmailNotificationDto("Support Requests", "Test Org", "Receive support requests from users", "admin", true,
                    false, false, false, false,
                    true, false, false, false, false),
            new UserEmailNotificationDto("Firmware Upgrade Failures", "Test Org",
                    "Receive automated firmware upgrade failure emails",
                    "operator", true, false, false, false, false,
                    true, false, false, false, false));

    private static final Organization operatorOrg = new Organization();
    private static final Organization adminOrg = new Organization();

    @BeforeEach
    void setUp() {
        authToken = Mockito.mock(CvManagerAuthToken.class);
        operatorOrg.setId(1);
        operatorOrg.setName("Test Organization");

        adminOrg.setId(2);
        adminOrg.setName("Admin Organization");
    }

    @Nested
    @DisplayName("GET /users/subscriptions/email-subscriptions — list all subscriptions")
    class GetAllSubscriptions {

        @Test
        @DisplayName("returns 403 when no permissions are granted (unauthenticated)")
        void noPermissions_returns403() throws Exception {
            // Spring Security filter runs before argument binding; unauthenticated → 403
            mockMvc.perform(get("/users/subscriptions/email-subscriptions"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 403 when authenticated but neither isSuperUser nor hasRole('USER')")
        void authenticated_insufficientPermissions_returns403() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(false);
            when(permissionService.hasRole(UserRole.USER)).thenReturn(false);

            mockMvc.perform(get("/users/subscriptions/email-subscriptions"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with subscriptions list when has only user role")
        void authenticated_returns200User() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(false);
            when(permissionService.hasRole(UserRole.USER)).thenReturn(true);
            when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
            when(authToken.getEmail()).thenReturn(email);
            when(authToken.getOrgRoles()).thenReturn(Map.of(1, Pair.of(operatorOrg, UserRole.USER)));
            when(emailService.getAllEmailSubscriptionOptionsForUser(email,
                    Map.of("Test Organization", UserRole.USER), false))
                    .thenReturn(validSubscriptionsList);

            mockMvc.perform(get("/users/subscriptions/email-subscriptions"))
                    .andExpect(jsonPath("$.subscriptions").isArray())
                    .andExpect(jsonPath("$.subscriptions[0].category").value("Support Requests"))
                    .andExpect(jsonPath("$.subscriptions[1].category").value("Firmware Upgrade Failures"))
                    .andExpect(jsonPath("$.email").value(email));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with subscriptions list when has operator role")
        void authenticated_returns200Operator() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(false);
            when(permissionService.hasRole(UserRole.USER)).thenReturn(true);
            when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
            when(authToken.getEmail()).thenReturn(email);
            when(authToken.getOrgRoles()).thenReturn(Map.of(1, Pair.of(operatorOrg, UserRole.OPERATOR)));
            when(emailService.getAllEmailSubscriptionOptionsForUser(email,
                    Map.of("Test Organization", UserRole.OPERATOR), false))
                    .thenReturn(validSubscriptionsList);

            mockMvc.perform(get("/users/subscriptions/email-subscriptions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subscriptions").isArray())
                    .andExpect(jsonPath("$.subscriptions[0].category").value("Support Requests"))
                    .andExpect(jsonPath("$.subscriptions[1].category").value("Firmware Upgrade Failures"))
                    .andExpect(jsonPath("$.email").value(email));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with subscriptions list when has admin role")
        void authenticated_returns200Admin() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(false);
            when(permissionService.hasRole(UserRole.USER)).thenReturn(true);
            when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
            when(authToken.getEmail()).thenReturn(email);
            when(authToken.getOrgRoles()).thenReturn(Map.of(
                    1, Pair.of(operatorOrg, UserRole.OPERATOR),
                    2, Pair.of(adminOrg, UserRole.ADMIN)));
            when(emailService.getAllEmailSubscriptionOptionsForUser(email,
                    Map.of("Test Organization", UserRole.OPERATOR, "Admin Organization", UserRole.ADMIN), false))
                    .thenReturn(validSubscriptionsList);

            mockMvc.perform(get("/users/subscriptions/email-subscriptions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subscriptions").isArray())
                    .andExpect(jsonPath("$.subscriptions[0].category").value("Support Requests"))
                    .andExpect(jsonPath("$.subscriptions[1].category").value("Firmware Upgrade Failures"))
                    .andExpect(jsonPath("$.email").value(email));
        }
    }

    @Nested
    @DisplayName("POST /users/subscriptions/email-subscriptions — update all subscriptions")
    class UpdateAllSubscriptions {

        @Test
        @DisplayName("returns 403 when no permissions are granted (unauthenticated)")
        void noPermissions_returns403() throws Exception {
            // Spring Security filter runs before argument binding; unauthenticated → 403
            mockMvc.perform(post("/users/subscriptions/email-subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validSubscriptionsList)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 403 when authenticated but neither isSuperUser nor hasRole('USER')")
        void authenticated_insufficientPermissions_returns403() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(false);
            when(permissionService.hasRole(UserRole.USER)).thenReturn(false);

            mockMvc.perform(post("/users/subscriptions/email-subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validSubscriptionsList)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with valid subscriptions list")
        void superUser_returns200() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(true);
            when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
            when(authToken.getEmail()).thenReturn(email);
            when(authToken.getOrgRoles()).thenReturn(Map.of(1, Pair.of(operatorOrg, UserRole.USER)));
            doNothing().when(emailService).updateEmailSubscriptions(email,
                    Map.of("Test Organization", UserRole.ADMIN), true, validSubscriptionsList);

            mockMvc.perform(post("/users/subscriptions/email-subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validSubscriptionsList)))
                    .andExpect(status().isOk());

            // Super users are treated as ADMIN in each of their organizations
            verify(emailService).updateEmailSubscriptions(email,
                    Map.of("Test Organization", UserRole.ADMIN), true, validSubscriptionsList);
        }

        @Test
        @WithMockUser
        @DisplayName("returns 400 with invalid subscriptions list")
        void superUser_returns400() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(true);
            when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
            when(authToken.getEmail()).thenReturn(email);
            when(authToken.getOrgRoles()).thenReturn(Map.of(1, Pair.of(operatorOrg, UserRole.USER)));
            doNothing().when(emailService).updateEmailSubscriptions(email,
                    Map.of("Test Organization", UserRole.ADMIN), true, validSubscriptionsList);

            mockMvc.perform(post("/users/subscriptions/email-subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"invalid\": true}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
