package us.dot.its.jpo.ode.api.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import us.dot.its.jpo.conflictmonitor.monitor.models.notifications.ConnectionOfTravelNotification;
import us.dot.its.jpo.conflictmonitor.monitor.models.notifications.Notification;
import us.dot.its.jpo.ode.api.accessors.counts.CountsRepository;
import us.dot.its.jpo.ode.api.accessors.notifications.active_notification.ActiveNotificationRepository;
import us.dot.its.jpo.ode.api.emails.generators.IntersectionNotificationSummaryEmailGenerator;
import us.dot.its.jpo.ode.api.models.MessageCount;
import us.dot.its.jpo.ode.api.models.emails.EmailCategory;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.EmailFrequency;
import us.dot.its.jpo.ode.api.models.emails.EmailRecipient;
import us.dot.its.jpo.ode.api.models.emails.contents.message_counts.MessageCountEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.message_counts.MessageCountRsuItem;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.services.EmailService;

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
    private OrganizationRepository organizationRepository;

    private EmailTask emailTask;

    private final int maximumResponseSize = 10;

    @BeforeEach
    void setUp() {
        emailTask = new EmailTask(emailService, activeNotificationRepo, maximumResponseSize, emailGenerator,
                countsRepository, organizationRepository, "test");
    }

    Notification createNotification(String key, String heading, String text, int intersectionId, long generatedAt) {
        Notification n = new ConnectionOfTravelNotification();
        n.key = key;
        n.setNotificationHeading(heading);
        n.setNotificationText(text);
        n.setIntersectionID(intersectionId);
        n.setNotificationGeneratedAt(generatedAt);
        return n;
    }

    @Test
    void testSendDailyCountEmails() {
        when(organizationRepository.findAllOrganizationNames()).thenReturn(List.of("Test Organization"));
        when(countsRepository.getRsuOrganizationMessageCounts(eq("Test Organization"), eq("BSM"), anyLong(),
                anyLong()))
                .thenReturn(List.of(new MessageCount("BSM", "192.168.1.1", 100L, 95L, "I-25")));
        when(countsRepository.getRsuOrganizationMessageCounts(eq("Test Organization"), eq("Map"), anyLong(),
                anyLong()))
                .thenReturn(List.of(new MessageCount("MAP", "192.168.1.1", 3600L, 1L, "I-25")));
        when(countsRepository.getRsuOrganizationMessageCounts(eq("Test Organization"), eq("SPaT"), anyLong(),
                anyLong()))
                .thenReturn(List.of(new MessageCount("SPAT", "192.168.1.1", 50L, 50L, "I-25")));
        when(countsRepository.getRsuOrganizationMessageCounts(eq("Test Organization"), eq("TIM"), anyLong(),
                anyLong()))
                .thenReturn(Collections.emptyList());
        when(countsRepository.getRsuOrganizationMessageCounts(eq("Test Organization"), eq("SRM"), anyLong(),
                anyLong()))
                .thenReturn(Collections.emptyList());
        when(countsRepository.getRsuOrganizationMessageCounts(eq("Test Organization"), eq("SSM"), anyLong(),
                anyLong()))
                .thenReturn(Collections.emptyList());

        emailTask.sendDailyCountEmails();

        ArgumentCaptor<MessageCountEmailContents> captor = ArgumentCaptor.forClass(MessageCountEmailContents.class);
        verify(emailService, times(1)).sendMessageCounts(captor.capture());
        verify(emailService, never()).sendEmails(anyList(), any());

        MessageCountEmailContents sent = captor.getValue();
        assertThat(sent.getOrganizationName()).isEqualTo("Test Organization");
        assertThat(sent.getDeploymentTitle()).isEqualTo("test Environment Counts - Test Organization");
        assertThat(sent.getMessageTypeList()).containsExactly("BSM", "TIM", "Map", "SPaT", "SRM", "SSM");
        assertThat(sent.getStartDate()).isNotNull();
        assertThat(sent.getEndDate()).isNotNull();
        assertThat(sent.getRsuCounts()).hasSize(1);

        MessageCountRsuItem rsuItem = sent.getRsuCounts().get(0);
        assertThat(rsuItem.getRsuIp()).isEqualTo("192.168.1.1");
        assertThat(rsuItem.getPrimaryRoute()).isEqualTo("I-25");
        assertThat(rsuItem.getMessageCountsByType().get("BSM").getIn()).isEqualTo(100);
        assertThat(rsuItem.getMessageCountsByType().get("BSM").getOut()).isEqualTo(95);
        assertThat(rsuItem.getMessageCountsByType().get("Map").getIn()).isEqualTo(3600);
        assertThat(rsuItem.getMessageCountsByType().get("Map").getOut()).isEqualTo(1);
        assertThat(rsuItem.getMessageCountsByType().get("SPaT").getIn()).isEqualTo(50);
        assertThat(rsuItem.getMessageCountsByType().get("SPaT").getOut()).isEqualTo(50);
        assertThat(rsuItem.getMessageCountsByType().get("TIM").getIn()).isEqualTo(0);
        assertThat(rsuItem.getMessageCountsByType().get("TIM").getOut()).isEqualTo(0);
        assertThat(rsuItem.getMessageCountsByType().get("SRM").getIn()).isEqualTo(0);
        assertThat(rsuItem.getMessageCountsByType().get("SSM").getIn()).isEqualTo(0);
    }

    @Test
    void testSendDailyCountEmails_EmptyCounts() {
        when(organizationRepository.findAllOrganizationNames()).thenReturn(List.of("Test Organization"));
        when(countsRepository.getRsuOrganizationMessageCounts(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(Collections.emptyList());

        emailTask.sendDailyCountEmails();

        verify(emailService, never()).sendMessageCounts(any());
        verify(emailService, never()).sendEmails(any(), any());
    }

    @Test
    void testToMessageCountEmailContentsGroupsByRsuAndCanonicalizesTypes() {
        List<MessageCount> counts = new ArrayList<>();
        counts.add(new MessageCount("BSM", "10.0.0.1", 10L, 9L, "US-36"));
        counts.add(new MessageCount("MAP", "10.0.0.1", 3600L, 1L, "US-36"));
        counts.add(new MessageCount("SPAT", "10.0.0.2", 20L, 20L, "I-70"));

        LocalDateTime end = LocalDateTime.of(2026, 9, 2, 0, 0, 0);
        LocalDateTime start = end.minusDays(1);

        MessageCountEmailContents contents = emailTask.toMessageCountEmailContents("CDOT", counts, start, end);

        assertThat(contents.getOrganizationName()).isEqualTo("CDOT");
        assertThat(contents.getRsuCounts()).hasSize(2);

        MessageCountRsuItem first = contents.getRsuCounts().get(0);
        assertThat(first.getRsuIp()).isEqualTo("10.0.0.1");
        assertThat(first.getPrimaryRoute()).isEqualTo("US-36");
        assertThat(first.getMessageCountsByType()).containsKeys("BSM", "TIM", "Map", "SPaT", "SRM", "SSM");
        assertThat(first.getMessageCountsByType().get("BSM").getIn()).isEqualTo(10);
        assertThat(first.getMessageCountsByType().get("Map").getOut()).isEqualTo(1);

        MessageCountRsuItem second = contents.getRsuCounts().get(1);
        assertThat(second.getRsuIp()).isEqualTo("10.0.0.2");
        assertThat(second.getMessageCountsByType().get("SPaT").getIn()).isEqualTo(20);
        assertThat(second.getMessageCountsByType().get("BSM").getIn()).isEqualTo(0);
    }

    @Test
    void testToMessageCountEmailContentsSkipsNullIps() {
        List<MessageCount> counts = List.of(new MessageCount("BSM", null, 10L, 9L, "US-36"));
        LocalDateTime end = LocalDateTime.of(2026, 9, 2, 0, 0, 0);

        MessageCountEmailContents contents = emailTask.toMessageCountEmailContents("CDOT", counts, end.minusDays(1),
                end);

        assertThat(contents.getRsuCounts()).isEmpty();
    }

    @Test
    void testGetActiveNotificationsReturnsContent() {
        Notification n1 = createNotification("k1", "h1", "t1", 1, Instant.now().toEpochMilli());
        Notification n2 = createNotification("k2", "h2", "t2", 2, Instant.now().toEpochMilli());
        List<Notification> notifications = Arrays.asList(n1, n2);
        Page<Notification> page = new PageImpl<>(notifications, PageRequest.of(0, maximumResponseSize),
                notifications.size());
        when(activeNotificationRepo.find(null, null, null, PageRequest.of(0, maximumResponseSize))).thenReturn(page);

        List<Notification> result = emailTask.getActiveNotifications();
        assertThat(result).containsExactly(n1, n2);
    }

    @Test
    void testGetNewNotificationsFindsNew() {
        Notification old1 = createNotification("k1", "h1", "t1", 1, 1000);
        Notification new1 = createNotification("k2", "h2", "t2", 2, 2000);
        List<Notification> oldList = Collections.singletonList(old1);
        List<Notification> newList = Arrays.asList(old1, new1);

        List<Notification> result = emailTask.getNewNotifications(newList, oldList);
        assertThat(result).containsExactly(new1);
    }

    @Test
    void testGetNewNotificationsNoneNew() {
        Notification old1 = createNotification("k1", "h1", "t1", 1, 1000);
        List<Notification> oldList = Collections.singletonList(old1);
        List<Notification> newList = Collections.singletonList(old1);

        List<Notification> result = emailTask.getNewNotifications(newList, oldList);
        assertThat(result).isEmpty();
    }

    @Test
    void testSendHourlyNotificationsFirstRunSetsLastHourList() {
        Notification n1 = createNotification("k1", "h1", "t1", 1, 1000);
        List<Notification> notifications = Collections.singletonList(n1);
        Page<Notification> page = new PageImpl<>(notifications, PageRequest.of(0, maximumResponseSize),
                notifications.size());
        when(activeNotificationRepo.find(null, null, null, PageRequest.of(0, maximumResponseSize))).thenReturn(page);

        emailTask.sendHourlyNotifications();

        // Should set lastHourList and not send email
        verify(emailService, never()).sendEmails(anyList(), any());
    }

    @Test
    void testSendHourlyNotificationsSendsEmailOnNew() {
        Notification old1 = createNotification("k1", "h1", "t1", 1, 1000);
        Notification new1 = createNotification("k2", "h2", "t2", 2, 2000);
        List<Notification> oldList = Collections.singletonList(old1);
        List<Notification> newList = Arrays.asList(old1, new1);

        Page<Notification> page1 = new PageImpl<>(oldList, PageRequest.of(0, maximumResponseSize), oldList.size());
        Page<Notification> page2 = new PageImpl<>(newList, PageRequest.of(0, maximumResponseSize), newList.size());

        when(activeNotificationRepo.find(null, null, null, PageRequest.of(0, maximumResponseSize)))
                .thenReturn(page1)
                .thenReturn(page2);

        emailTask.sendHourlyNotifications();

        List<EmailRecipient> recipients = List.of(new EmailRecipient("email", "name"));
        when(emailService.getUsersForNotificationType(EmailCategory.INTERSECTION_NOTIFICATION_SUMMARY,
                EmailFrequency.ONCE_PER_HOUR)).thenReturn(recipients);

        EmailContent content = new EmailContent("subject", "body");
        when(emailGenerator.generateEmailBody(any())).thenReturn(content);

        emailTask.sendHourlyNotifications();

        verify(emailService).sendEmails(eq(recipients), eq(content));
    }

    @Test
    void testDoNotSendHourlyNotificationsWhenNoRecipients() {
        Notification old1 = createNotification("k1", "h1", "t1", 1, 1000);
        Notification new1 = createNotification("k2", "h2", "t2", 2, 2000);
        List<Notification> oldList = Collections.singletonList(old1);
        List<Notification> newList = Arrays.asList(old1, new1);

        Page<Notification> page1 = new PageImpl<>(oldList, PageRequest.of(0, maximumResponseSize), oldList.size());
        Page<Notification> page2 = new PageImpl<>(newList, PageRequest.of(0, maximumResponseSize), newList.size());

        when(activeNotificationRepo.find(null, null, null, PageRequest.of(0, maximumResponseSize)))
                .thenReturn(page1)
                .thenReturn(page2);

        emailTask.sendHourlyNotifications();

        List<EmailRecipient> recipients = Collections.emptyList();
        when(emailService.getUsersForNotificationType(EmailCategory.INTERSECTION_NOTIFICATION_SUMMARY,
                EmailFrequency.ONCE_PER_HOUR)).thenReturn(recipients);

        emailTask.sendHourlyNotifications();

        verify(emailService, never()).sendEmails(anyList(), any(EmailContent.class));
    }

    @Test
    void testSendDailyNotificationsFirstRunSetsLastDayList() {
        Notification n1 = createNotification("k1", "h1", "t1", 1, 1000);
        List<Notification> notifications = Collections.singletonList(n1);
        Page<Notification> page = new PageImpl<>(notifications, PageRequest.of(0, maximumResponseSize),
                notifications.size());
        when(activeNotificationRepo.find(null, null, null, PageRequest.of(0, maximumResponseSize))).thenReturn(page);

        emailTask.sendDailyNotifications();

        verify(emailService, never()).sendEmails(anyList(), any());
    }

    @Test
    void testSendDailyNotificationsSendsEmailOnNew() {
        Notification old1 = createNotification("k1", "h1", "t1", 1, 1000);
        Notification new1 = createNotification("k2", "h2", "t2", 2, 2000);
        List<Notification> oldList = Collections.singletonList(old1);
        List<Notification> newList = Arrays.asList(old1, new1);

        Page<Notification> page1 = new PageImpl<>(oldList, PageRequest.of(0, maximumResponseSize), oldList.size());
        Page<Notification> page2 = new PageImpl<>(newList, PageRequest.of(0, maximumResponseSize), newList.size());

        when(activeNotificationRepo.find(null, null, null, PageRequest.of(0, maximumResponseSize)))
                .thenReturn(page1)
                .thenReturn(page2);

        emailTask.sendDailyNotifications();

        List<EmailRecipient> recipients = List.of(new EmailRecipient("email", "name"));
        when(emailService.getUsersForNotificationType(EmailCategory.INTERSECTION_NOTIFICATION_SUMMARY,
                EmailFrequency.ONCE_PER_DAY)).thenReturn(recipients);

        EmailContent content = new EmailContent("subject", "body");
        when(emailGenerator.generateEmailBody(any())).thenReturn(content);

        emailTask.sendDailyNotifications();

        verify(emailService).sendEmails(eq(recipients), eq(content));
    }

    @Test
    void testSendWeeklyNotificationsFirstRunSetsLastWeekList() {
        Notification n1 = createNotification("k1", "h1", "t1", 1, 1000);
        List<Notification> notifications = Collections.singletonList(n1);
        Page<Notification> page = new PageImpl<>(notifications, PageRequest.of(0, maximumResponseSize),
                notifications.size());
        when(activeNotificationRepo.find(null, null, null, PageRequest.of(0, maximumResponseSize))).thenReturn(page);

        emailTask.sendWeeklyNotifications();

        verify(emailService, never()).sendEmails(anyList(), any());
    }

    @Test
    void testSendWeeklyNotificationsSendsEmailOnNew() {
        Notification old1 = createNotification("k1", "h1", "t1", 1, 1000);
        Notification new1 = createNotification("k2", "h2", "t2", 2, 2000);
        List<Notification> oldList = Collections.singletonList(old1);
        List<Notification> newList = Arrays.asList(old1, new1);

        Page<Notification> page1 = new PageImpl<>(oldList, PageRequest.of(0, maximumResponseSize), oldList.size());
        Page<Notification> page2 = new PageImpl<>(newList, PageRequest.of(0, maximumResponseSize), newList.size());

        when(activeNotificationRepo.find(null, null, null, PageRequest.of(0, maximumResponseSize)))
                .thenReturn(page1)
                .thenReturn(page2);

        emailTask.sendWeeklyNotifications();

        List<EmailRecipient> recipients = List.of(new EmailRecipient("email", "name"));
        when(emailService.getUsersForNotificationType(EmailCategory.INTERSECTION_NOTIFICATION_SUMMARY,
                EmailFrequency.ONCE_PER_WEEK)).thenReturn(recipients);

        EmailContent content = new EmailContent("subject", "body");
        when(emailGenerator.generateEmailBody(any())).thenReturn(content);

        emailTask.sendWeeklyNotifications();

        verify(emailService).sendEmails(eq(recipients), eq(content));
    }

    @Test
    void testSendMonthlyNotificationsFirstRunSetsLastMonthList() {
        Notification n1 = createNotification("k1", "h1", "t1", 1, 1000);
        List<Notification> notifications = Collections.singletonList(n1);
        Page<Notification> page = new PageImpl<>(notifications, PageRequest.of(0, maximumResponseSize),
                notifications.size());
        when(activeNotificationRepo.find(null, null, null, PageRequest.of(0, maximumResponseSize))).thenReturn(page);

        emailTask.sendMonthlyNotifications();

        verify(emailService, never()).sendEmails(anyList(), any());
    }

    @Test
    void testSendMonthlyNotificationsSendsEmailOnNew() {
        Notification old1 = createNotification("k1", "h1", "t1", 1, 1000);
        Notification new1 = createNotification("k2", "h2", "t2", 2, 2000);
        List<Notification> oldList = Collections.singletonList(old1);
        List<Notification> newList = Arrays.asList(old1, new1);

        Page<Notification> page1 = new PageImpl<>(oldList, PageRequest.of(0, maximumResponseSize), oldList.size());
        Page<Notification> page2 = new PageImpl<>(newList, PageRequest.of(0, maximumResponseSize), newList.size());

        when(activeNotificationRepo.find(null, null, null, PageRequest.of(0, maximumResponseSize)))
                .thenReturn(page1)
                .thenReturn(page2);

        emailTask.sendMonthlyNotifications();

        List<EmailRecipient> recipients = List.of(new EmailRecipient("email", "name"));
        when(emailService.getUsersForNotificationType(EmailCategory.INTERSECTION_NOTIFICATION_SUMMARY,
                EmailFrequency.ONCE_PER_MONTH)).thenReturn(recipients);

        EmailContent content = new EmailContent("subject", "body");
        when(emailGenerator.generateEmailBody(any())).thenReturn(content);

        emailTask.sendMonthlyNotifications();

        verify(emailService).sendEmails(eq(recipients), eq(content));
    }
}
