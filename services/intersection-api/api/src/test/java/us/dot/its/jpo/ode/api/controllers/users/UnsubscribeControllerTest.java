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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.UserEmailNotificationDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.Role;
import us.dot.its.jpo.ode.api.models.postgres.tables.UserOrganization;
import us.dot.its.jpo.ode.api.repositories.UserOrganizationRepository;
import us.dot.its.jpo.ode.api.models.emails.EmailSubscriptionGetResponse;
import us.dot.its.jpo.ode.api.services.EmailService;

@ExtendWith(MockitoExtension.class)
public class UnsubscribeControllerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private UnsubscribeTokenGenerator tokenGenerator;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    private UnsubscribeController userController;

    private static final String VALID_TOKEN = "valid-token-123";
    private static final String INVALID_TOKEN = "invalid-token-456";
    private static final String TEST_EMAIL = "test@example.com";

    private static final List<UserEmailNotificationDto> SUBSCRIPTION_LIST = Arrays.asList(
            new UserEmailNotificationDto("Support Requests", "Receive support requests from users", "admin", true,
                    false, false, false, false,
                    true, false, false, false, false),
            new UserEmailNotificationDto("Firmware Upgrade Failures",
                    "Receive automated firmware upgrade failure emails",
                    "operator", true, false, false, false, false,
                    true, false, false, false, false),
            new UserEmailNotificationDto("Intersection Notification Summary",
                    "Receive automated intersection notification summary emails", "user", true, false, false, false,
                    false,
                    true, true, true, true, true),
            new UserEmailNotificationDto("Daily Message Counts", "Receive automated daily message count emails", "user",
                    false, false, false, false, false,
                    true, false, false, false, false),
            new UserEmailNotificationDto("Access Requests", "Receive organization access requests from users", "admin",
                    false, false, false, false, false,
                    true, false, false, false, false),
            new UserEmailNotificationDto("Critical Error Messages", "Receive automated critical error message emails",
                    "operator", false, false, false, false, false,
                    true, false, false, false, false));

    @BeforeEach
    void setUp() {
        userController = new UnsubscribeController(emailService, userOrganizationRepository, tokenGenerator);
    }

    @Test
    void testUpdateEmailSubscriptions_ValidToken() {

        when(tokenGenerator.parseAndValidateToken(VALID_TOKEN)).thenReturn(TEST_EMAIL);
        when(emailService.updateEmailSubscriptions(TEST_EMAIL, SUBSCRIPTION_LIST)).thenReturn(0);

        userController.updateEmailSubscriptions(VALID_TOKEN, SUBSCRIPTION_LIST);

        verify(tokenGenerator).parseAndValidateToken(VALID_TOKEN);
        verify(emailService).updateEmailSubscriptions(TEST_EMAIL, SUBSCRIPTION_LIST);
    }

    @Test
    void testUpdateEmailSubscriptions_InvalidToken() {
        when(tokenGenerator.parseAndValidateToken(INVALID_TOKEN)).thenReturn(null);

        assertThrows(NotAuthorizedException.class,
                () -> userController.updateEmailSubscriptions(INVALID_TOKEN, SUBSCRIPTION_LIST));

        verify(tokenGenerator).parseAndValidateToken(INVALID_TOKEN);
    }

    @Test
    void testGetEmailSubscriptions_ValidToken_UserWithSubscriptions() {
        Role roleOperator = mock(Role.class);
        when(roleOperator.getName()).thenReturn("operator");
        UserOrganization orgOperator = mock(UserOrganization.class);
        when(orgOperator.getRole()).thenReturn(roleOperator);

        Role roleAdmin = mock(Role.class);
        when(roleAdmin.getName()).thenReturn("admin");
        UserOrganization orgAdmin = mock(UserOrganization.class);
        when(orgAdmin.getRole()).thenReturn(roleAdmin);

        List<UserOrganization> authToken = Arrays.asList(orgAdmin, orgOperator);
        when(userOrganizationRepository.findAllByEmail(TEST_EMAIL)).thenReturn(authToken);

        when(tokenGenerator.parseAndValidateToken(VALID_TOKEN)).thenReturn(TEST_EMAIL);
        when(emailService.getAllEmailSubscriptionOptionsForUser(TEST_EMAIL, true, true)).thenReturn(SUBSCRIPTION_LIST);

        EmailSubscriptionGetResponse response = userController.getEmailSubscriptions(VALID_TOKEN);

        assertNotNull(response);
        assertEquals(TEST_EMAIL, response.getEmail());

        assertEquals(SUBSCRIPTION_LIST, response.getSubscriptions());

        verify(tokenGenerator).parseAndValidateToken(VALID_TOKEN);
        verify(emailService).getAllEmailSubscriptionOptionsForUser(TEST_EMAIL, true, true);
    }

    @Test
    void testGetEmailSubscriptions_InvalidToken() {
        when(tokenGenerator.parseAndValidateToken(INVALID_TOKEN)).thenReturn(null);

        assertThrows(NotAuthorizedException.class, () -> userController.getEmailSubscriptions(INVALID_TOKEN));

        verify(tokenGenerator).parseAndValidateToken(INVALID_TOKEN);
        verify(emailService, never()).getAllEmailSubscriptionOptionsForUser(eq(TEST_EMAIL), anyBoolean(), anyBoolean());
    }
}
