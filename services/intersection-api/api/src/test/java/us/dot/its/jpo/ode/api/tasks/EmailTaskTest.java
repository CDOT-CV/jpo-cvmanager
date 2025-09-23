package us.dot.its.jpo.ode.api.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import us.dot.its.jpo.conflictmonitor.monitor.models.notifications.Notification;
import us.dot.its.jpo.conflictmonitor.monitor.models.notifications.SignalStateConflictNotification;
import us.dot.its.jpo.ode.api.accessors.notifications.active_notification.ActiveNotificationRepository;
import us.dot.its.jpo.ode.api.emails.generators.IntersectionNotificationSummaryEmailGenerator;
import us.dot.its.jpo.ode.api.models.emails.*;
import us.dot.its.jpo.ode.api.services.EmailService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailTaskTest {

    @Mock
    private EmailService emailService;

    @Mock
    private ActiveNotificationRepository activeNotificationRepo;

    @Mock
    private IntersectionNotificationSummaryEmailGenerator emailGenerator;

    private EmailTask emailTask;
    private List<Notification> notifications;
    private List<EmailRecipient> recipients;
    private EmailContent emailContent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        emailTask = new EmailTask(emailService, activeNotificationRepo, 10, emailGenerator);

        notifications = List.of(
                createNotification("key1", "Heading 1", "Text 1", 101, System.currentTimeMillis()),
                createNotification("key2", "Heading 2", "Text 2", 101, System.currentTimeMillis()));

        recipients = List.of(
                new EmailRecipient("user1@example.com", null),
                new EmailRecipient("user2@example.com", null));

        emailContent = new EmailContent("Subject", "Body");

        when(activeNotificationRepo.find(null, null, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(notifications));
        when(emailService.getUsersForNotificationType(any(), any()))
                .thenReturn(recipients);
        when(emailGenerator.generateEmailBody(any()))
                .thenReturn(emailContent);
    }

    Notification createNotification(String key, String heading, String text, int intersectionId, long generatedAt) {
        Notification n = new SignalStateConflictNotification();
        n.key = key;
        n.setNotificationHeading(heading);
        n.setNotificationText(text);
        n.setIntersectionID(intersectionId);
        n.setNotificationGeneratedAt(generatedAt);
        return n;
    }

    @Test
    void testSendAlwaysNotifications() {
        emailTask.sendAlwaysNotifications();

        verify(emailService, times(1)).sendEmails(recipients, emailContent);
    }

    @Test
    void testSendHourlyNotifications() {
        emailTask.sendHourlyNotifications();

        verify(emailService, times(1)).sendEmails(recipients, emailContent);
    }

    @Test
    void testSendDailyNotifications() {
        emailTask.sendDailyNotifications();

        verify(emailService, times(1)).sendEmails(recipients, emailContent);
    }

    @Test
    void testSendWeeklyNotifications() {
        emailTask.sendWeeklyNotifications();

        verify(emailService, times(1)).sendEmails(recipients, emailContent);
    }

    @Test
    void testSendMonthlyNotifications() {
        emailTask.sendMonthlyNotifications();

        verify(emailService, times(1)).sendEmails(recipients, emailContent);
    }

    @Test
    void testGetActiveNotifications() {
        List<Notification> result = emailTask.getActiveNotifications();

        assertEquals(notifications, result);
        verify(activeNotificationRepo, times(1))
                .find(null, null, null, PageRequest.of(0, 10));
    }

    @Test
    void testGetNewNotifications() {
        List<Notification> oldList = List
                .of(createNotification("key1", "Heading 1", "Text 1", 101, System.currentTimeMillis()));
        List<Notification> newList = List.of(
                createNotification("key1", "Heading 1", "Text 1", 101, System.currentTimeMillis()),
                createNotification("key2", "Heading 2", "Text 2", 101, System.currentTimeMillis()));

        List<Notification> result = emailTask.getNewNotifications(newList, oldList);

        assertEquals(1, result.size());
        assertEquals("key2", result.get(0).getKey());
    }
}