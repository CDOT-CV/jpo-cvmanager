package us.dot.its.jpo.ode.api.tasks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import us.dot.its.jpo.ode.api.accessors.counts.CountsRepository;
import us.dot.its.jpo.ode.api.accessors.notifications.active_notification.ActiveNotificationRepository;
import us.dot.its.jpo.ode.api.emails.generators.DailyCountEmailGenerator;
import us.dot.its.jpo.ode.api.emails.generators.IntersectionNotificationSummaryEmailGenerator;
import us.dot.its.jpo.ode.api.models.MessageCount;
import us.dot.its.jpo.ode.api.models.emails.EmailCategory;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.EmailFrequency;
import us.dot.its.jpo.ode.api.models.emails.EmailRecipient;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
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

    @Mock
    private RsuRepository rsuRepository;

    @Mock
    private DailyCountEmailGenerator dailyCountEmailGenerator;

    private EmailTask emailTask;

    @BeforeEach
    void setUp() {
        emailTask = new EmailTask(emailService, activeNotificationRepo, 1000, emailGenerator,
                countsRepository, organizationRepository, rsuRepository, dailyCountEmailGenerator, "test");
    }

    @Test
    void testSendDailyCountEmails() throws Exception {
        Rsu rsu = new Rsu();
        rsu.setIpv4Address(InetAddress.getByName("192.168.1.1"));
        rsu.setPrimaryRoute("I-25");

        List<MessageCount> mockCounts = new ArrayList<>();
        mockCounts.add(new MessageCount("BSM", "192.168.1.1", 100L, 95L, "I-25"));

        List<EmailRecipient> recipients = List.of(new EmailRecipient("test@example.com", null));
        EmailContent content = new EmailContent("Test Subject", "Test Body");

        when(organizationRepository.findAllOrganizationNames()).thenReturn(List.of("Test Organization"));
        when(rsuRepository.findAllByOrganization(eq("Test Organization"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rsu)));
        when(countsRepository.getRsuOrganizationMessageCounts(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(mockCounts);
        when(emailService.getUsersForNotificationTypeByOrganization(
                eq(EmailCategory.MESSAGE_COUNTS), eq("Test Organization"), eq(EmailFrequency.ONCE_PER_DAY)))
                .thenReturn(recipients);
        when(dailyCountEmailGenerator.generateEmailBody(any())).thenReturn(content);

        emailTask.sendDailyCountEmails();

        verify(dailyCountEmailGenerator, times(1)).generateEmailBody(any());
        verify(emailService, times(1)).sendEmails(recipients, content);
    }

    @Test
    void testSendDailyCountEmails_NoRecipients() throws Exception {
        Rsu rsu = new Rsu();
        rsu.setIpv4Address(InetAddress.getByName("192.168.1.1"));
        rsu.setPrimaryRoute("I-25");

        when(organizationRepository.findAllOrganizationNames()).thenReturn(List.of("Test Organization"));
        when(rsuRepository.findAllByOrganization(eq("Test Organization"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rsu)));
        when(countsRepository.getRsuOrganizationMessageCounts(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(List.of(new MessageCount("BSM", "192.168.1.1", 100L, 95L, "I-25")));
        when(emailService.getUsersForNotificationTypeByOrganization(
                eq(EmailCategory.MESSAGE_COUNTS), eq("Test Organization"), eq(EmailFrequency.ONCE_PER_DAY)))
                .thenReturn(List.of());

        emailTask.sendDailyCountEmails();

        verify(dailyCountEmailGenerator, times(0)).generateEmailBody(any());
        verify(emailService, times(0)).sendEmails(any(), any());
    }
}
