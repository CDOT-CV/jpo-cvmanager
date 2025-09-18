package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import us.dot.its.jpo.ode.api.emails.generators.*;
import us.dot.its.jpo.ode.api.emails.providers.EmailProvider;
import us.dot.its.jpo.ode.api.models.emails.*;
import us.dot.its.jpo.ode.api.models.emails.contents.FirmwareUpgradeFailureEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.RsuErrorSummaryEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.SupportRequestEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.access_requests.AccessRequestEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.message_counts.MessageCountEmailContents;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    @Mock
    private EmailProvider emailProvider;
    @Mock
    private PostgresService postgresService;
    @Mock
    private SupportRequestEmailGenerator supportRequestEmailGenerator;
    @Mock
    private AccessRequestEmailGenerator accessRequestEmailGenerator;
    @Mock
    private MessageCountEmailGenerator messageCountEmailGenerator;
    @Mock
    private FirmwareUpgradeFailureEmailGenerator firmwareUpgradeFailureEmailGenerator;
    @Mock
    private RsuErrorSummaryEmailGenerator rsuErrorSummaryEmailGenerator;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSendEmails() {
        List<EmailRecipient> recipients = List.of(new EmailRecipient("test@example.com", null));
        EmailContent content = new EmailContent("subject", "body");
        doNothing().when(emailProvider).sendBatchedEmails(recipients, content);

        emailService.sendEmails(recipients, content);

        verify(emailProvider, times(1)).sendBatchedEmails(recipients, content);
    }

    @Test
    void testGetUsersForNotificationType() {
        when(postgresService.getUsersByNotificationType("SUPPORT_REQUEST"))
                .thenReturn(List.of("user1@example.com", "user2@example.com"));

        List<EmailRecipient> recipients = emailService.getUsersForNotificationType(
                EmailCategory.SUPPORT_REQUEST, EmailFrequency.ALWAYS);

        assertEquals(2, recipients.size());
        assertEquals("user1@example.com", recipients.get(0).getEmail());
        assertEquals("user2@example.com", recipients.get(1).getEmail());
    }

    @Test
    void testGetSupportRequestEmailList_WithOrg() {
        when(postgresService.getOrganizationEmailListByRole("org", "ADMIN"))
                .thenReturn(List.of("admin1@example.com"));
        List<String> result = emailService.getSupportRequestEmailList("org");
        assertEquals(1, result.size());
        assertEquals("admin1@example.com", result.get(0));
    }

    @Test
    void testGetSupportRequestEmailList_FallbackToSuperUser() {
        when(postgresService.getOrganizationEmailListByRole("org", "ADMIN"))
                .thenReturn(new ArrayList<>());
        when(postgresService.getSuperUserEmailList())
                .thenReturn(List.of("superuser@example.com"));
        List<String> result = emailService.getSupportRequestEmailList("org");
        assertEquals(1, result.size());
        assertEquals("superuser@example.com", result.get(0));
    }

    @Test
    void testSendSupportRequest() {
        SupportRequestEmailContents data = new SupportRequestEmailContents();
        EmailContent content = new EmailContent("subject", "body");
        List<EmailRecipient> recipients = List.of(new EmailRecipient("test@example.com", null));
        List<EmailSendResponse> responses = List.of(new EmailSendResponse(0, "OK"));

        when(supportRequestEmailGenerator.generateEmailBody(data)).thenReturn(content);
        when(postgresService.getUsersByNotificationType(anyString())).thenReturn(List.of("test@example.com"));
        when(emailProvider.sendBatchedEmails(recipients, content)).thenReturn(responses);

        List<EmailSendResponse> result = emailService.sendSupportRequest(data);

        assertEquals(responses, result);
    }

    @Test
    void testSendAccessRequest() {
        AccessRequestEmailContents data = new AccessRequestEmailContents();
        EmailContent content = new EmailContent("subject", "body");
        List<EmailRecipient> recipients = List.of(new EmailRecipient("test@example.com", null));
        List<EmailSendResponse> responses = List.of(new EmailSendResponse(0, "OK"));

        when(accessRequestEmailGenerator.generateEmailBody(data)).thenReturn(content);
        when(postgresService.getUsersByNotificationType(anyString())).thenReturn(List.of("test@example.com"));
        when(emailProvider.sendBatchedEmails(recipients, content)).thenReturn(responses);

        List<EmailSendResponse> result = emailService.sendAccessRequest(data);

        assertEquals(responses, result);
    }

    @Test
    void testSendMessageCounts() {
        MessageCountEmailContents data = new MessageCountEmailContents();
        EmailContent content = new EmailContent("subject", "body");
        List<EmailRecipient> recipients = List.of(new EmailRecipient("test@example.com", null));
        List<EmailSendResponse> responses = List.of(new EmailSendResponse(0, "OK"));

        when(messageCountEmailGenerator.generateEmailBody(data)).thenReturn(content);
        when(postgresService.getUsersByNotificationType(anyString())).thenReturn(List.of("test@example.com"));
        when(emailProvider.sendBatchedEmails(recipients, content)).thenReturn(responses);

        List<EmailSendResponse> result = emailService.sendMessageCounts(data);

        assertEquals(responses, result);
    }

    @Test
    void testSendFirmwareUpgradeFailure() {
        FirmwareUpgradeFailureEmailContents data = new FirmwareUpgradeFailureEmailContents();
        EmailContent content = new EmailContent("subject", "body");
        List<EmailRecipient> recipients = List.of(new EmailRecipient("test@example.com", null));
        List<EmailSendResponse> responses = List.of(new EmailSendResponse(0, "OK"));

        when(firmwareUpgradeFailureEmailGenerator.generateEmailBody(data)).thenReturn(content);
        when(postgresService.getUsersByNotificationType(anyString())).thenReturn(List.of("test@example.com"));
        when(emailProvider.sendBatchedEmails(recipients, content)).thenReturn(responses);

        List<EmailSendResponse> result = emailService.sendFirmwareUpgradeFailure(data);

        assertEquals(responses, result);
    }

    @Test
    void testSendRsuErrorSummary() {
        RsuErrorSummaryEmailContents data = new RsuErrorSummaryEmailContents();
        EmailContent content = new EmailContent("subject", "body");
        List<EmailRecipient> recipients = List.of(new EmailRecipient("test@example.com", null));
        List<EmailSendResponse> responses = List.of(new EmailSendResponse(0, "OK"));

        when(rsuErrorSummaryEmailGenerator.generateEmailBody(data)).thenReturn(content);
        when(postgresService.getUsersByNotificationType(anyString())).thenReturn(List.of("test@example.com"));
        when(emailProvider.sendBatchedEmails(recipients, content)).thenReturn(responses);

        List<EmailSendResponse> result = emailService.sendRsuErrorSummary(data);

        assertEquals(responses, result);
    }
}