package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import us.dot.its.jpo.ode.api.emails.generators.*;
import us.dot.its.jpo.ode.api.emails.providers.EmailProvider;
import us.dot.its.jpo.ode.api.models.emails.EmailCategory;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.EmailFrequency;
import us.dot.its.jpo.ode.api.models.emails.EmailRecipient;
import us.dot.its.jpo.ode.api.models.emails.EmailSendResponse;
import us.dot.its.jpo.ode.api.models.emails.contents.IntersectionNotificationSummaryEmailContents;
import us.dot.its.jpo.ode.api.models.postgres.derived.EmailSubscription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    @Mock
    private EmailProvider emailProvider;
    @Mock
    private PostgresService postgresService;
    @Mock
    private IntersectionNotificationSummaryEmailGenerator intersectionNotificationSummaryEmailGenerator;

    @InjectMocks
    private EmailService emailService;

    private static final String TEST_EMAIL = "user@example.com";

    private static final List<EmailSubscription> SUBSCRIPTION_LIST = Arrays.asList(
            new EmailSubscription("Support Requests", "Receive support requests from users", "admin", true, false,
                    false, false, false,
                    true, false, false, false, false),
            new EmailSubscription("Firmware Upgrade Failures", "Receive automated firmware upgrade failure emails",
                    "operator", true, false, false, false, false,
                    true, false, false, false, false),
            new EmailSubscription("Intersection Notification Summary",
                    "Receive automated intersection notification summary emails", "user", true, false, false, false,
                    false,
                    true, true, true, true, true),
            new EmailSubscription("Daily Message Counts", "Receive automated daily message count emails", "user",
                    false, false, false, false, false,
                    true, false, false, false, false),
            new EmailSubscription("Access Requests", "Receive organization access requests from users", "admin", false,
                    false, false, false, false,
                    true, false, false, false, false),
            new EmailSubscription("Critical Error Messages", "Receive automated critical error message emails",
                    "operator", false, false, false, false, false,
                    true, false, false, false, false));

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSendEmails() {
        List<EmailRecipient> recipients = List.of(new EmailRecipient("test@example.com", null));
        EmailContent content = new EmailContent("subject", "body");
        doReturn(List.of()).when(emailProvider).sendBatchedEmails(recipients, content);

        emailService.sendEmails(recipients, content);

        verify(emailProvider, times(1)).sendBatchedEmails(recipients, content);
    }

    @Test
    void testGetUsersForNotificationType() {
        when(postgresService.getUsersByNotificationType("Support Requests", EmailFrequency.IMMEDIATE))
                .thenReturn(List.of("user1@example.com", "user2@example.com"));

        List<EmailRecipient> recipients = emailService.getUsersForNotificationType(
                EmailCategory.SUPPORT_REQUEST, EmailFrequency.IMMEDIATE);

        assertEquals(2, recipients.size());
        assertEquals("user1@example.com", recipients.get(0).getEmail());
        assertEquals("user2@example.com", recipients.get(1).getEmail());
    }

    @Test
    void testGetUsersForNotificationTypeByRsu() {
        when(postgresService.getUsersByNotificationTypeAndRsu("Support Requests", "1.1.1.1", EmailFrequency.IMMEDIATE))
                .thenReturn(List.of("user1@example.com", "user2@example.com"));

        List<EmailRecipient> recipients = emailService.getUsersForNotificationTypeByRsu(
                EmailCategory.SUPPORT_REQUEST, "1.1.1.1", EmailFrequency.IMMEDIATE);

        assertEquals(2, recipients.size());
        assertEquals("user1@example.com", recipients.get(0).getEmail());
        assertEquals("user2@example.com", recipients.get(1).getEmail());
    }

    @Test
    void testGetUsersForNotificationTypeByOrganization() {
        when(postgresService.getUsersByNotificationTypeAndOrganization("Support Requests", "Test Org",
                EmailFrequency.IMMEDIATE))
                .thenReturn(List.of("user1@example.com", "user2@example.com"));

        List<EmailRecipient> recipients = emailService.getUsersForNotificationTypeByOrganization(
                EmailCategory.SUPPORT_REQUEST, "Test Org", EmailFrequency.IMMEDIATE);

        assertEquals(2, recipients.size());
        assertEquals("user1@example.com", recipients.get(0).getEmail());
        assertEquals("user2@example.com", recipients.get(1).getEmail());
    }

    @Test
    void testSendIntersectionNotificationSummaryEmailSendResponses() {
        IntersectionNotificationSummaryEmailContents data = new IntersectionNotificationSummaryEmailContents();
        EmailContent content = new EmailContent("subject", "body");
        List<EmailRecipient> recipients = List.of(new EmailRecipient("test@example.com", null));
        List<EmailSendResponse> responses = List.of(new EmailSendResponse(0, "OK"));

        when(intersectionNotificationSummaryEmailGenerator.generateEmailBody(data)).thenReturn(content);
        when(postgresService.getUsersByNotificationType(anyString(), any())).thenReturn(List.of("test@example.com"));
        when(emailProvider.sendBatchedEmails(recipients, content)).thenReturn(responses);

        List<EmailSendResponse> result = emailService.sendIntersectionNotificationSummaryEmailSendResponses(data);

        assertEquals(responses, result);
    }

    @Test
    void testUpdateEmailSubscriptions_NoChange() {

        List<EmailSubscription> emailSubscriptions = List.of(SUBSCRIPTION_LIST.get(0), SUBSCRIPTION_LIST.get(1),
                SUBSCRIPTION_LIST.get(2));

        when(postgresService.getEmailSubscriptionsByUser(TEST_EMAIL)).thenReturn(emailSubscriptions);

        Integer numModified = emailService.updateEmailSubscriptions(TEST_EMAIL, SUBSCRIPTION_LIST);

        assertEquals(0, numModified);
        verify(postgresService).getEmailSubscriptionsByUser(TEST_EMAIL);
        verify(postgresService, never()).removeEmailSubscriptionsByUser(eq(TEST_EMAIL), any());
        verify(postgresService, never()).addEmailSubscriptionByUser(eq(TEST_EMAIL), anyString());
    }

    @Test
    void testUpdateEmailSubscriptions_AddSubscriptions() {
        List<EmailSubscription> subscriptionList = new ArrayList<>(SUBSCRIPTION_LIST);
        subscriptionList.set(2, new EmailSubscription("Intersection Notification Summary",
                "Receive automated intersection notification summary emails", "user", true, false, false, false, false,
                true, true, true, true, true));
        subscriptionList.set(3, new EmailSubscription("Daily Message Counts",
                "Receive automated daily message count emails", "user", true, false, false, false, false,
                true, false, false, false, false));

        when(postgresService.getEmailSubscriptionsByUser(TEST_EMAIL)).thenReturn(SUBSCRIPTION_LIST);

        Integer numModified = emailService.updateEmailSubscriptions(TEST_EMAIL, subscriptionList);

        assertEquals(1, numModified);
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
                "Receive automated intersection notification summary emails", "user", false, false, false, false, false,
                true, true, true, true, true));
        subscriptionList.set(3, new EmailSubscription("Daily Message Counts",
                "Receive automated daily message count emails", "user", false, false, false, false, false,
                true, false, false, false, false));

        when(postgresService.getEmailSubscriptionsByUser(TEST_EMAIL)).thenReturn(SUBSCRIPTION_LIST);

        Integer numModified = emailService.updateEmailSubscriptions(TEST_EMAIL, subscriptionList);

        assertEquals(1, numModified);
        verify(postgresService).getEmailSubscriptionsByUser(TEST_EMAIL);
        verify(postgresService).removeEmailSubscriptionsByUser(TEST_EMAIL,
                List.of("Intersection Notification Summary"));
        verify(postgresService, never()).removeEmailSubscriptionsByUser(TEST_EMAIL, List.of("Daily Message Counts"));
        verify(postgresService, never()).addEmailSubscriptionByUser(eq(TEST_EMAIL), anyString());
    }

    @Test
    void testUpdateEmailSubscriptions_UpdateSubscriptions() {
        List<EmailSubscription> subscriptionList = new ArrayList<>(SUBSCRIPTION_LIST);
        subscriptionList.set(2, new EmailSubscription("Intersection Notification Summary",
                "Receive automated intersection notification summary emails", "user", true, true, false, false, false,
                true, true, true, true, true));
        subscriptionList.set(3, new EmailSubscription("Daily Message Counts",
                "Receive automated daily message count emails", "user", false, false, false, false, false,
                true, false, false, false, false));

        when(postgresService.getEmailSubscriptionsByUser(TEST_EMAIL)).thenReturn(SUBSCRIPTION_LIST);

        Integer numModified = emailService.updateEmailSubscriptions(TEST_EMAIL, subscriptionList);

        assertEquals(1, numModified);
        verify(postgresService).getEmailSubscriptionsByUser(TEST_EMAIL);
        verify(postgresService).updateEmailSubscriptionByUser(TEST_EMAIL, subscriptionList.get(2));
        verify(postgresService, never()).removeEmailSubscriptionsByUser(eq(TEST_EMAIL), anyList());
        verify(postgresService, never()).addEmailSubscriptionByUser(eq(TEST_EMAIL), anyString());
    }

    @Test
    void testUpdateEmailSubscriptions_MixedAddAndRemove() {
        List<EmailSubscription> subscriptionList = new ArrayList<>(SUBSCRIPTION_LIST);
        subscriptionList.set(2, new EmailSubscription("Intersection Notification Summary",
                "Receive automated intersection notification summary emails", "user", false, false, false, false, false,
                true, true, true, true, true));
        subscriptionList.set(3, new EmailSubscription("Daily Message Counts",
                "Receive automated daily message count emails", "user", true, false, false, false, false,
                true, false, false, false, false));

        when(postgresService.getEmailSubscriptionsByUser(TEST_EMAIL)).thenReturn(SUBSCRIPTION_LIST);

        Integer numModified = emailService.updateEmailSubscriptions(TEST_EMAIL, subscriptionList);

        assertEquals(2, numModified);
        verify(postgresService).getEmailSubscriptionsByUser(TEST_EMAIL);
        verify(postgresService).removeEmailSubscriptionsByUser(TEST_EMAIL,
                List.of("Intersection Notification Summary"));
        verify(postgresService).addEmailSubscriptionByUser(TEST_EMAIL, "Daily Message Counts");
    }

    @Test
    void testGetEmailSubscriptions_UserWithSubscriptions() {

        when(postgresService.getEmailSubscriptionTypes()).thenReturn(SUBSCRIPTION_LIST);
        when(postgresService.getEmailSubscriptionsByUser(TEST_EMAIL)).thenReturn(SUBSCRIPTION_LIST);

        List<EmailSubscription> subscriptions = emailService.getAllEmailSubscriptionOptionsForUser(TEST_EMAIL);

        assertNotNull(subscriptions);
        assertEquals(6, subscriptions.size());

        assertEquals(SUBSCRIPTION_LIST, subscriptions);

        verify(postgresService).getEmailSubscriptionTypes();
        verify(postgresService).getEmailSubscriptionsByUser(TEST_EMAIL);
    }
}