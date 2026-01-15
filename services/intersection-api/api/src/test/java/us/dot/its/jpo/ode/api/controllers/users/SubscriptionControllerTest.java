package us.dot.its.jpo.ode.api.controllers.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import us.dot.its.jpo.ode.api.models.emails.EmailSubscriptionGetResponse;
import us.dot.its.jpo.ode.api.models.postgres.derived.EmailSubscription;
import us.dot.its.jpo.ode.api.models.postgres.tables.EmailType;
import us.dot.its.jpo.ode.api.services.PostgresService;

@ExtendWith(MockitoExtension.class)
public class SubscriptionControllerTest {

    @Mock
    private PostgresService postgresService;

    @Mock
    private JwtAuthenticationToken authentication;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Jwt jwtToken;

    @InjectMocks
    private SubscriptionController userController;

    private static final String TEST_EMAIL = "user@example.com";

    private static final List<EmailType> EMAIL_TYPE_LIST = Arrays.asList(
            new EmailType(1, "Support Requests", "Receive support requests from users", 1, true, false, false, false,
                    false),
            new EmailType(2, "Firmware Upgrade Failures", "Receive automated firmware upgrade failure emails",
                    2, true, false, false, false, false),
            new EmailType(3, "Intersection Notification Summary",
                    "Receive automated intersection notification summary emails", 3, true, false, false, false, false),
            new EmailType(4, "Daily Message Counts", "Receive automated daily message count emails", 3, true, false,
                    false, false, false),
            new EmailType(5, "Access Requests", "Receive organization access requests from users", 1, true, false,
                    false, false, false),
            new EmailType(6, "Critical Error Messages", "Receive automated critical error message emails", 2, true,
                    false, false, false, false));

    private static final List<EmailSubscription> SUBSCRIPTION_LIST = Arrays.asList(
            new EmailSubscription("Support Requests", "Receive support requests from users", "admin", true),
            new EmailSubscription("Firmware Upgrade Failures", "Receive automated firmware upgrade failure emails",
                    "operator", true),
            new EmailSubscription("Intersection Notification Summary",
                    "Receive automated intersection notification summary emails", "user", true),
            new EmailSubscription("Daily Message Counts", "Receive automated daily message count emails", "user",
                    false),
            new EmailSubscription("Access Requests", "Receive organization access requests from users", "admin", false),
            new EmailSubscription("Critical Error Messages", "Receive automated critical error message emails",
                    "operator", false));

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getToken()).thenReturn(jwtToken);
        when(jwtToken.getClaimAsString("preferred_username")).thenReturn("user@example.com");
        userController = new SubscriptionController(postgresService);
    }

    @Test
    void testUpdateEmailSubscriptions_NoChange() {

        List<EmailType> emailTypes = List.of(EMAIL_TYPE_LIST.get(0), EMAIL_TYPE_LIST.get(1),
                EMAIL_TYPE_LIST.get(2));

        when(postgresService.getEmailSubscriptionsByUser(TEST_EMAIL)).thenReturn(emailTypes);

        ResponseEntity<String> response = userController.updateEmailSubscriptions(SUBSCRIPTION_LIST);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(postgresService).getEmailSubscriptionsByUser(TEST_EMAIL);
        verify(postgresService, never()).removeEmailSubscriptionsByUser(eq(TEST_EMAIL), any());
        verify(postgresService, never()).addEmailSubscriptionByUser(eq(TEST_EMAIL), anyString());
    }

    @Test
    void testUpdateEmailSubscriptions_AddSubscriptions() {
        List<EmailSubscription> subscriptionList = new ArrayList<>(SUBSCRIPTION_LIST);
        subscriptionList.set(2, new EmailSubscription("Intersection Notification Summary",
                "Receive automated intersection notification summary emails", "user", true));
        subscriptionList.add(new EmailSubscription("Daily Message Counts",
                "Receive automated daily message count emails", "user", true));

        List<EmailType> emailTypes = List.of(EMAIL_TYPE_LIST.get(0), EMAIL_TYPE_LIST.get(1),
                EMAIL_TYPE_LIST.get(2));

        when(postgresService.getEmailSubscriptionsByUser(TEST_EMAIL)).thenReturn(emailTypes);

        ResponseEntity<String> response = userController.updateEmailSubscriptions(subscriptionList);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(postgresService).getEmailSubscriptionsByUser(TEST_EMAIL);
        verify(postgresService, never()).removeEmailSubscriptionsByUser(eq(TEST_EMAIL), any());
        verify(postgresService, never()).addEmailSubscriptionByUser(eq(TEST_EMAIL),
                eq("Intersection Notification Summary"));
        verify(postgresService).addEmailSubscriptionByUser(TEST_EMAIL, "Daily Message Counts");
    }

    @Test
    void testUpdateEmailSubscriptions_RemoveSubscriptions() {
        List<EmailSubscription> subscriptionList = new ArrayList<>(SUBSCRIPTION_LIST);
        subscriptionList.set(2, new EmailSubscription("Intersection Notification Summary",
                "Receive automated intersection notification summary emails", "user", false));
        subscriptionList.add(new EmailSubscription("Daily Message Counts",
                "Receive automated daily message count emails", "user", false));

        List<EmailType> emailTypes = List.of(EMAIL_TYPE_LIST.get(0), EMAIL_TYPE_LIST.get(1),
                EMAIL_TYPE_LIST.get(2));

        when(postgresService.getEmailSubscriptionsByUser(TEST_EMAIL)).thenReturn(emailTypes);

        ResponseEntity<String> response = userController.updateEmailSubscriptions(subscriptionList);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(postgresService).getEmailSubscriptionsByUser(TEST_EMAIL);
        verify(postgresService).removeEmailSubscriptionsByUser(TEST_EMAIL,
                List.of("Intersection Notification Summary"));
        verify(postgresService, never()).removeEmailSubscriptionsByUser(TEST_EMAIL, List.of("Daily Message Counts"));
        verify(postgresService, never()).addEmailSubscriptionByUser(eq(TEST_EMAIL), anyString());
    }

    @Test
    void testUpdateEmailSubscriptions_MixedAddAndRemove() {
        List<EmailSubscription> subscriptionList = new ArrayList<>(SUBSCRIPTION_LIST);
        subscriptionList.set(2, new EmailSubscription("Intersection Notification Summary",
                "Receive automated intersection notification summary emails", "user", false));
        subscriptionList.add(new EmailSubscription("Daily Message Counts",
                "Receive automated daily message count emails", "user", true));

        List<EmailType> emailTypes = List.of(EMAIL_TYPE_LIST.get(0), EMAIL_TYPE_LIST.get(1),
                EMAIL_TYPE_LIST.get(2));

        when(postgresService.getEmailSubscriptionsByUser(TEST_EMAIL)).thenReturn(emailTypes);

        ResponseEntity<String> response = userController.updateEmailSubscriptions(subscriptionList);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(postgresService).getEmailSubscriptionsByUser(TEST_EMAIL);
        verify(postgresService).removeEmailSubscriptionsByUser(TEST_EMAIL,
                List.of("Intersection Notification Summary"));
        verify(postgresService).addEmailSubscriptionByUser(TEST_EMAIL, "Daily Message Counts");
    }

    @Test
    void testGetEmailSubscriptions_UserWithSubscriptions() {
        List<EmailType> emailTypeList = List.of(EMAIL_TYPE_LIST.get(0), EMAIL_TYPE_LIST.get(1), EMAIL_TYPE_LIST.get(2));

        when(postgresService.getEmailSubscriptionTypes()).thenReturn(SUBSCRIPTION_LIST);
        when(postgresService.getEmailSubscriptionsByUser(TEST_EMAIL)).thenReturn(emailTypeList);

        ResponseEntity<EmailSubscriptionGetResponse> response = userController.getEmailSubscriptions();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(TEST_EMAIL, response.getBody().getEmail());

        assertEquals(SUBSCRIPTION_LIST, response.getBody().getSubscriptions());

        verify(postgresService).getEmailSubscriptionTypes();
        verify(postgresService).getEmailSubscriptionsByUser(TEST_EMAIL);
    }
}
