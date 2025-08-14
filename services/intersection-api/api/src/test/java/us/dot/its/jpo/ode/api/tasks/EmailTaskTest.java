package us.dot.its.jpo.ode.api.tasks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import us.dot.its.jpo.conflictmonitor.monitor.models.notifications.SignalStateConflictNotification;
import us.dot.its.jpo.ode.api.accessors.counts.CountsRepository;
import us.dot.its.jpo.ode.api.accessors.notifications.active_notification.ActiveNotificationRepository;
import us.dot.its.jpo.ode.api.emails.generators.DailyCountEmailGenerator;
import us.dot.its.jpo.ode.api.emails.generators.IntersectionNotificationSummaryEmailGenerator;
import us.dot.its.jpo.ode.api.models.MessageCount;
import us.dot.its.jpo.ode.api.models.emails.EmailWrapper;
import us.dot.its.jpo.ode.api.services.EmailService;
import us.dot.its.jpo.ode.api.services.PostgresService;

@ExtendWith(MockitoExtension.class)
public class EmailTaskTest {

    @Mock
    private EmailService emailService;

    @Mock
    private ActiveNotificationRepository activeNotificationRepo;

    @Mock
    private IntersectionNotificationSummaryEmailGenerator emailGenerator;

    @Mock
    private CountsRepository countsRepository;

    @Mock
    private PostgresService postgresService;

    @Mock
    private DailyCountEmailGenerator dailyCountEmailGenerator;

    private EmailTask emailTask;

    @BeforeEach
    void setUp() {
        emailTask = new EmailTask(emailService, activeNotificationRepo, 1000, emailGenerator,
                countsRepository, postgresService, dailyCountEmailGenerator);
    }

    @Test
    void testSendDailyNotificationsWithCountEmails() {
        // Given
        List<SignalStateConflictNotification> currentNotifications = new ArrayList<>();
        SignalStateConflictNotification notification = new SignalStateConflictNotification();
        notification.setKey("test-key");
        currentNotifications.add(notification);

        List<String> organizations = List.of("Test Organization");
        Map<String, String> rsuIpToRoadMap = new HashMap<>();
        rsuIpToRoadMap.put("192.168.1.1", "I-25");

        List<MessageCount> mockCounts = new ArrayList<>();
        MessageCount count1 = new MessageCount("BSM", "192.168.1.1", 100L, 95L, "I-25");
        mockCounts.add(count1);

        List<String> recipients = List.of("test@example.com");
        List<EmailWrapper> emailWrappers = new ArrayList<>();
        EmailWrapper wrapper = new EmailWrapper("test@example.com", "Test Subject", "Test Body", "http://unsubscribe");
        emailWrappers.add(wrapper);

        // When
        when(activeNotificationRepo.find(any(), any(), any(), any())).thenReturn(null);
        when(postgresService.getAllOrganizations()).thenReturn(organizations);
        when(postgresService.getOrganizationRsuIps(anyString())).thenReturn(rsuIpToRoadMap);
        when(countsRepository.getRsuOrganizationMessageCounts(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(mockCounts);
        when(emailService.getUsersForNotificationType(any(), any())).thenReturn(recipients);
        when(dailyCountEmailGenerator.generateEmailBodies(any(), any())).thenReturn(emailWrappers);

        // Then
        emailTask.sendDailyNotifications();

        // Verify that both notification emails and count emails are sent
        verify(emailService, times(2)).getUsersForNotificationType(any(), any());
        verify(emailService, times(2)).sendEmails(any());
    }

    @Test
    void testSendDailyNotificationsWithCountEmailErrors() {
        // Given
        List<SignalStateConflictNotification> currentNotifications = new ArrayList<>();
        SignalStateConflictNotification notification = new SignalStateConflictNotification();
        notification.setKey("test-key");
        currentNotifications.add(notification);

        List<String> organizations = List.of("Test Organization");
        Map<String, String> rsuIpToRoadMap = new HashMap<>();
        rsuIpToRoadMap.put("192.168.1.1", "I-25");

        List<String> recipients = List.of("test@example.com");
        List<EmailWrapper> emailWrappers = new ArrayList<>();
        EmailWrapper wrapper = new EmailWrapper("test@example.com", "Test Subject", "Test Body", "http://unsubscribe");
        emailWrappers.add(wrapper);

        // When - simulate an error in count email processing
        when(activeNotificationRepo.find(any(), any(), any(), any())).thenReturn(null);
        when(postgresService.getAllOrganizations()).thenReturn(organizations);
        when(postgresService.getOrganizationRsuIps(anyString())).thenReturn(rsuIpToRoadMap);
        when(countsRepository.getRsuOrganizationMessageCounts(anyString(), anyString(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("Count repository error"));
        when(emailService.getUsersForNotificationType(any(), any())).thenReturn(recipients);
        when(dailyCountEmailGenerator.generateEmailBodies(any(), any())).thenReturn(emailWrappers);

        // Then - should not throw exception, count emails should fail gracefully
        emailTask.sendDailyNotifications();

        // Verify that notification emails are still sent despite count email errors
        verify(emailService, times(1)).getUsersForNotificationType(any(), any());
        verify(emailService, times(1)).sendEmails(any());
    }

    @Test
    void testSendDailyNotificationsWithNoOrganizations() {
        // Given
        List<SignalStateConflictNotification> currentNotifications = new ArrayList<>();
        SignalStateConflictNotification notification = new SignalStateConflictNotification();
        notification.setKey("test-key");
        currentNotifications.add(notification);

        List<String> organizations = new ArrayList<>();

        List<String> recipients = List.of("test@example.com");
        List<EmailWrapper> emailWrappers = new ArrayList<>();
        EmailWrapper wrapper = new EmailWrapper("test@example.com", "Test Subject", "Test Body", "http://unsubscribe");
        emailWrappers.add(wrapper);

        // When
        when(activeNotificationRepo.find(any(), any(), any(), any())).thenReturn(null);
        when(postgresService.getAllOrganizations()).thenReturn(organizations);
        when(emailService.getUsersForNotificationType(any(), any())).thenReturn(recipients);
        when(dailyCountEmailGenerator.generateEmailBodies(any(), any())).thenReturn(emailWrappers);

        // Then
        emailTask.sendDailyNotifications();

        // Verify that notification emails are still sent
        verify(emailService, times(1)).getUsersForNotificationType(any(), any());
        verify(emailService, times(1)).sendEmails(any());
        // Verify that count repository is not called when no organizations exist
        verify(countsRepository, times(0)).getRsuOrganizationMessageCounts(anyString(), anyString(), anyLong(),
                anyLong());
    }

    @Test
    void testSendDailyNotificationsFirstRun() {
        // Given - first run scenario where lastDayList is null
        List<String> organizations = List.of("Test Organization");
        Map<String, String> rsuIpToRoadMap = new HashMap<>();
        rsuIpToRoadMap.put("192.168.1.1", "I-25");

        List<MessageCount> mockCounts = new ArrayList<>();
        MessageCount count1 = new MessageCount("BSM", "192.168.1.1", 100L, 95L, "I-25");
        mockCounts.add(count1);

        List<String> recipients = List.of("test@example.com");
        List<EmailWrapper> emailWrappers = new ArrayList<>();
        EmailWrapper wrapper = new EmailWrapper("test@example.com", "Test Subject", "Test Body", "http://unsubscribe");
        emailWrappers.add(wrapper);

        // When - first run (lastDayList is null)
        when(activeNotificationRepo.find(any(), any(), any(), any())).thenReturn(null);
        when(postgresService.getAllOrganizations()).thenReturn(organizations);
        when(postgresService.getOrganizationRsuIps(anyString())).thenReturn(rsuIpToRoadMap);
        when(countsRepository.getRsuOrganizationMessageCounts(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(mockCounts);
        when(emailService.getUsersForNotificationType(any(), any())).thenReturn(recipients);
        when(dailyCountEmailGenerator.generateEmailBodies(any(), any())).thenReturn(emailWrappers);

        // Then
        emailTask.sendDailyNotifications();

        // Verify that count emails are still sent even on first run
        verify(emailService, times(1)).getUsersForNotificationType(any(), any());
        verify(emailService, times(1)).sendEmails(any());
        verify(countsRepository, times(6)).getRsuOrganizationMessageCounts(anyString(), anyString(), anyLong(),
                anyLong());
    }
}