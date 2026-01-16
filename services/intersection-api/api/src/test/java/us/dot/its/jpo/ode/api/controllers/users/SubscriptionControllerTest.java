package us.dot.its.jpo.ode.api.controllers.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import us.dot.its.jpo.ode.api.services.EmailService;

@ExtendWith(MockitoExtension.class)
public class SubscriptionControllerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private JwtAuthenticationToken authentication;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Jwt jwtToken;

    @InjectMocks
    private SubscriptionController userController;

    private static final String TEST_EMAIL = "user@example.com";

    private static final List<EmailSubscription> SUBSCRIPTION_LIST = Arrays.asList(
            new EmailSubscription("Support Requests", "Receive support requests from users", "admin", true, false, false, false, false,
                true, false, false, false, false),
            new EmailSubscription("Firmware Upgrade Failures", "Receive automated firmware upgrade failure emails",
                    "operator", true, false, false, false, false,
                true, false, false, false, false),
            new EmailSubscription("Intersection Notification Summary",
                    "Receive automated intersection notification summary emails", "user", true, false, false, false, false,
                true, true, true, true, true),
            new EmailSubscription("Daily Message Counts", "Receive automated daily message count emails", "user",
                    false, false, false, false, false,
                true, false, false, false, false),
            new EmailSubscription("Access Requests", "Receive organization access requests from users", "admin", false, false, false, false, false,
                true, false, false, false, false),
            new EmailSubscription("Critical Error Messages", "Receive automated critical error message emails",
                    "operator", false, false, false, false, false,
                true, false, false, false, false));

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getToken()).thenReturn(jwtToken);
        when(jwtToken.getClaimAsString("preferred_username")).thenReturn("user@example.com");
        userController = new SubscriptionController(emailService);
    }

    @Test
    void testUpdateEmailSubscriptions_Success() {

        when(emailService.updateEmailSubscriptions(TEST_EMAIL, SUBSCRIPTION_LIST)).thenReturn(0);

        ResponseEntity<String> response = userController.updateEmailSubscriptions(SUBSCRIPTION_LIST);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(emailService).updateEmailSubscriptions(TEST_EMAIL, SUBSCRIPTION_LIST);
    }

    @Test
    void testGetEmailSubscriptions_Success() {
        when(emailService.getAllEmailSubscriptionOptionsForUser(TEST_EMAIL)).thenReturn(SUBSCRIPTION_LIST);

        ResponseEntity<EmailSubscriptionGetResponse> response = userController.getEmailSubscriptions();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(TEST_EMAIL, response.getBody().getEmail());

        assertEquals(SUBSCRIPTION_LIST, response.getBody().getSubscriptions());

        verify(emailService).getAllEmailSubscriptionOptionsForUser(TEST_EMAIL);
    }
}
