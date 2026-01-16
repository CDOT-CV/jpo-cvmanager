package us.dot.its.jpo.ode.api.controllers.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailSubscriptionGetResponse;
import us.dot.its.jpo.ode.api.models.postgres.derived.EmailSubscription;
import us.dot.its.jpo.ode.api.services.EmailService;

@ExtendWith(MockitoExtension.class)
public class UnsubscribeControllerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private UnsubscribeTokenGenerator tokenGenerator;

    private UnsubscribeController userController;

    private static final String VALID_TOKEN = "valid-token-123";
    private static final String INVALID_TOKEN = "invalid-token-456";
    private static final String TEST_EMAIL = "test@example.com";

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
        userController = new UnsubscribeController(emailService, tokenGenerator);
    }

    @Test
    void testUpdateEmailSubscriptions_ValidToken() {

        when(tokenGenerator.parseAndValidateToken(VALID_TOKEN)).thenReturn(TEST_EMAIL);
        when(emailService.updateEmailSubscriptions(TEST_EMAIL, SUBSCRIPTION_LIST)).thenReturn(0);

        ResponseEntity<String> response = userController.updateEmailSubscriptions(VALID_TOKEN, SUBSCRIPTION_LIST);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(tokenGenerator).parseAndValidateToken(VALID_TOKEN);
        verify(emailService).updateEmailSubscriptions(TEST_EMAIL, SUBSCRIPTION_LIST);
    }    

    @Test
    void testUpdateEmailSubscriptions_InvalidToken() {
        when(tokenGenerator.parseAndValidateToken(INVALID_TOKEN)).thenReturn(null);

        ResponseEntity<String> response = userController.updateEmailSubscriptions(INVALID_TOKEN, SUBSCRIPTION_LIST);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(tokenGenerator).parseAndValidateToken(INVALID_TOKEN);
    }

    @Test
    void testGetEmailSubscriptions_ValidToken_UserWithSubscriptions() {

        when(tokenGenerator.parseAndValidateToken(VALID_TOKEN)).thenReturn(TEST_EMAIL);
        when(emailService.getAllEmailSubscriptionOptionsForUser(TEST_EMAIL)).thenReturn(SUBSCRIPTION_LIST);

        ResponseEntity<EmailSubscriptionGetResponse> response = userController.getEmailSubscriptions(VALID_TOKEN);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(TEST_EMAIL, response.getBody().getEmail());

        assertEquals(SUBSCRIPTION_LIST, response.getBody().getSubscriptions());

        verify(tokenGenerator).parseAndValidateToken(VALID_TOKEN);
        verify(emailService).getAllEmailSubscriptionOptionsForUser(TEST_EMAIL);
    }

    @Test
    void testGetEmailSubscriptions_InvalidToken() {
        // Arrange
        when(tokenGenerator.parseAndValidateToken(INVALID_TOKEN)).thenReturn(null);

        // Act
        ResponseEntity<EmailSubscriptionGetResponse> response = userController.getEmailSubscriptions(INVALID_TOKEN);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(tokenGenerator).parseAndValidateToken(INVALID_TOKEN);
        verify(emailService, never()).getAllEmailSubscriptionOptionsForUser(TEST_EMAIL);
    }
}
