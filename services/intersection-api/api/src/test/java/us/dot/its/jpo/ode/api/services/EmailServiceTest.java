package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import us.dot.its.jpo.ode.api.emails.generators.*;
import us.dot.its.jpo.ode.api.emails.providers.EmailProvider;
import us.dot.its.jpo.ode.api.mappers.UserEmailNotificationMapper;
import us.dot.its.jpo.ode.api.models.emails.EmailCategory;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.EmailFrequency;
import us.dot.its.jpo.ode.api.models.emails.EmailRecipient;
import us.dot.its.jpo.ode.api.models.emails.EmailSendResponse;
import us.dot.its.jpo.ode.api.models.emails.UserEmailNotificationDto;
import us.dot.its.jpo.ode.api.models.emails.contents.IntersectionNotificationSummaryEmailContents;
import us.dot.its.jpo.ode.api.models.postgres.tables.EmailType;
import us.dot.its.jpo.ode.api.models.postgres.tables.Role;
import us.dot.its.jpo.ode.api.models.postgres.tables.User;
import us.dot.its.jpo.ode.api.models.postgres.tables.UserEmailNotification;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;

import us.dot.its.jpo.ode.api.repositories.EmailTypeRepository;
import us.dot.its.jpo.ode.api.repositories.UserEmailNotificationRepository;
import us.dot.its.jpo.ode.api.repositories.UserRepository;

import java.net.InetAddress;

import static org.mockito.Mockito.*;

class EmailServiceTest {

    @Mock
    private EmailProvider emailProvider;
    @Mock
    private EmailTypeRepository emailTypeRepository;
    @Mock
    private UserEmailNotificationRepository userEmailNotificationRepository;
    @Mock
    private IntersectionNotificationSummaryEmailGenerator intersectionNotificationSummaryEmailGenerator;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserEmailNotificationMapper userEmailNotificationMapper;

    @InjectMocks
    private EmailService emailService;

    private static final String TEST_EMAIL = "user@example.com";
    private static final User TEST_USER = new User();
    private static final Role ROLE_USER = new Role();
    private static final Role ROLE_ADMIN = new Role();

    private static UserEmailNotification createUserEmailNotification(String category, String description,
            String roleName,
            boolean immediate, boolean hourly, boolean daily, boolean weekly, boolean monthly,
            boolean supports_immediate, boolean supports_hourly, boolean supports_daily, boolean supports_weekly,
            boolean supports_monthly) {
        EmailType emailType = createEmailType(category, description, roleName, supports_immediate, supports_hourly,
                supports_daily, supports_weekly,
                supports_monthly);

        UserEmailNotification notification = new UserEmailNotification();
        notification.setEmailType(emailType);
        notification.setUser(TEST_USER);
        notification.setImmediate(immediate);
        notification.setHourly(hourly);
        notification.setDaily(daily);
        notification.setWeekly(weekly);
        notification.setMonthly(monthly);

        return notification;
    }

    private static EmailType createEmailType(String category, String description,
            String roleName, boolean supports_immediate, boolean supports_hourly, boolean supports_daily,
            boolean supports_weekly,
            boolean supports_monthly) {
        EmailType emailType = new EmailType();
        emailType.setEmailType(category);
        emailType.setDescription(description);
        if (roleName.equals("user")) {
            emailType.setRequiredRole(ROLE_USER);
        } else if (roleName.equals("admin")) {
            emailType.setRequiredRole(ROLE_ADMIN);
        }
        emailType.setSupportsImmediate(supports_immediate);
        emailType.setSupportsHourly(supports_hourly);
        emailType.setSupportsDaily(supports_daily);
        emailType.setSupportsWeekly(supports_weekly);
        emailType.setSupportsMonthly(supports_monthly);

        return emailType;
    }

    // private static final List<UserEmailNotification> SUBSCRIPTION_LIST =
    // Arrays.asList(
    // createUserEmailNotification(
    // "Support Requests", "Receive support requests from users", "user",
    // true, false, false, false, false,
    // true, false, false, false, false),
    // createUserEmailNotification(
    // "Intersection Notification Summary", "Receive automated intersection
    // notification summary emails",
    // "user",
    // true, false, false, false, false,
    // true, true, true, true, true),
    // createUserEmailNotification(
    // "Daily Message Counts", "Receive automated daily message count emails",
    // "user",
    // false, false, false, false, false,
    // true, false, false, false, false),
    // createUserEmailNotification(
    // "Access Requests", "Receive organization access requests from users",
    // "admin",
    // false, false, false, false, false,
    // true, false, false, false, false));

    // private static final UserEmailNotificationDto SUPPORT_REQUEST_DTO = new
    // UserEmailNotificationDto(
    // "Support Requests", "Receive support requests from users", "admin",
    // true, false, false, false, false,
    // true, false, false, false, false);

    // private static final UserEmailNotificationDto
    // INTERSECTION_NOTIFICATION_SUMMARY_DTO = new UserEmailNotificationDto(
    // "Intersection Notification Summary", "Receive automated intersection
    // notification summary emails", "user",
    // true, false, false, false, false,
    // true, true, true, true, true);

    // private static final UserEmailNotificationDto DAILY_MESSAGE_COUNTS_DTO = new
    // UserEmailNotificationDto(
    // "Daily Message Counts", "Receive automated daily message count emails",
    // "user",
    // false, false, false, false, false,
    // true, false, false, false, false);

    // private static final UserEmailNotificationDto ACCESS_REQUESTS_DTO = new
    // UserEmailNotificationDto(
    // "Access Requests", "Receive organization access requests from users",
    // "admin",
    // false, false, false, false, false,
    // true, false, false, false, false);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        TEST_USER.setEmail(TEST_EMAIL);
        ROLE_USER.setName("user");
        ROLE_ADMIN.setName("admin");
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
        when(userEmailNotificationRepository.findUsersByNotificationType("Support Requests", "IMMEDIATE"))
                .thenReturn(List.of("user1@example.com", "user2@example.com"));

        List<EmailRecipient> recipients = emailService.getUsersForNotificationType(
                EmailCategory.SUPPORT_REQUEST, EmailFrequency.IMMEDIATE);

        assertEquals(2, recipients.size());
        assertEquals("user1@example.com", recipients.get(0).getEmail());
        assertEquals("user2@example.com", recipients.get(1).getEmail());
    }

    @Test
    void testGetUsersForNotificationTypeByRsu() throws Throwable {
        when(userEmailNotificationRepository.findUsersByNotificationTypeAndRsu("Support Requests",
                "IMMEDIATE", InetAddress.getByName("1.1.1.1")))
                .thenReturn(List.of("user1@example.com", "user2@example.com"));

        List<EmailRecipient> recipients = emailService.getUsersForNotificationTypeByRsu(
                EmailCategory.SUPPORT_REQUEST, "1.1.1.1", EmailFrequency.IMMEDIATE);

        assertEquals(2, recipients.size());
        assertEquals("user1@example.com", recipients.get(0).getEmail());
        assertEquals("user2@example.com", recipients.get(1).getEmail());
    }

    @Test
    void testGetUsersForNotificationTypeByOrganization() {
        when(userEmailNotificationRepository.findUsersByNotificationTypeAndOrganization("Support Requests", "IMMEDIATE",
                "Test Org"))
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
        when(userEmailNotificationRepository.findUsersByNotificationType(anyString(), any()))
                .thenReturn(List.of("test@example.com"));
        when(emailProvider.sendBatchedEmails(recipients, content)).thenReturn(responses);

        List<EmailSendResponse> result = emailService.sendIntersectionNotificationSummaryEmailSendResponses(data);

        assertEquals(responses, result);
    }

    @Test
    void testUpdateEmailSubscriptions_NoChange() {

        List<UserEmailNotification> SUBSCRIPTION_LIST = Arrays.asList(
                createUserEmailNotification(
                        "Support Requests", "Receive support requests from users", "user",
                        true, false, false, false, false,
                        true, false, false, false, false),
                createUserEmailNotification(
                        "Intersection Notification Summary",
                        "Receive automated intersection notification summary emails",
                        "user",
                        true, false, false, false, false,
                        true, true, true, true, true),
                createUserEmailNotification(
                        "Daily Message Counts", "Receive automated daily message count emails", "user",
                        false, false, false, false, false,
                        true, false, false, false, false),
                createUserEmailNotification(
                        "Access Requests", "Receive organization access requests from users", "admin",
                        false, false, false, false, false,
                        true, false, false, false, false));

        UserEmailNotificationDto SUPPORT_REQUEST_DTO = new UserEmailNotificationDto(
                "Support Requests", "Receive support requests from users", "admin",
                true, false, false, false, false,
                true, false, false, false, false);

        UserEmailNotificationDto INTERSECTION_NOTIFICATION_SUMMARY_DTO = new UserEmailNotificationDto(
                "Intersection Notification Summary", "Receive automated intersection notification summary emails",
                "user",
                true, false, false, false, false,
                true, true, true, true, true);

        UserEmailNotificationDto DAILY_MESSAGE_COUNTS_DTO = new UserEmailNotificationDto(
                "Daily Message Counts", "Receive automated daily message count emails",
                "user",
                false, false, false, false, false,
                true, false, false, false, false);

        UserEmailNotificationDto ACCESS_REQUESTS_DTO = new UserEmailNotificationDto(
                "Access Requests", "Receive organization access requests from users",
                "admin",
                false, false, false, false, false,
                true, false, false, false, false);

        List<UserEmailNotification> emailSubscriptions = SUBSCRIPTION_LIST;

        when(userEmailNotificationRepository.findNotificationsByUser(TEST_EMAIL)).thenReturn(emailSubscriptions);

        Integer numModified = emailService.updateEmailSubscriptions(TEST_EMAIL, List.of(SUPPORT_REQUEST_DTO,
                INTERSECTION_NOTIFICATION_SUMMARY_DTO, DAILY_MESSAGE_COUNTS_DTO, ACCESS_REQUESTS_DTO));

        verify(userEmailNotificationRepository, never()).deleteAll();
        verify(userEmailNotificationRepository, never()).saveAll(anyList());
        assertEquals(0, numModified);
        verify(userEmailNotificationRepository).findNotificationsByUser(TEST_EMAIL);
    }

    @Test
    void testUpdateEmailSubscriptions_AddSubscriptions() {

        List<UserEmailNotification> SUBSCRIPTION_LIST = Arrays.asList(
                createUserEmailNotification(
                        "Support Requests", "Receive support requests from users", "user",
                        true, false, false, false, false,
                        true, false, false, false, false),
                createUserEmailNotification(
                        "Intersection Notification Summary",
                        "Receive automated intersection notification summary emails",
                        "user",
                        true, false, false, false, false,
                        true, true, true, true, true),
                createUserEmailNotification(
                        "Daily Message Counts", "Receive automated daily message count emails", "user",
                        false, false, false, false, false,
                        true, false, false, false, false),
                createUserEmailNotification(
                        "Access Requests", "Receive organization access requests from users", "admin",
                        false, false, false, false, false,
                        true, false, false, false, false));

        UserEmailNotificationDto dailyMessageCountsDto = new UserEmailNotificationDto(
                "Daily Message Counts", "Receive automated daily message count emails", "user",
                true, false, false, false, false,
                true, false, false, false, false);
        UserEmailNotificationDto accessRequestDto = new UserEmailNotificationDto(
                "Access Requests", "Receive organization access requests from users", "admin",
                true, false, false, false, false,
                true, false, false, false, false);

        UserEmailNotification dailyMessageCounts = createUserEmailNotification(
                "Daily Message Counts", "Receive automated daily message count emails", "user",
                true, false, false, false, false,
                true, false, false, false, false);

        UserEmailNotification accessRequests = createUserEmailNotification(
                "Access Requests", "Receive organization access requests from users", "admin",
                true, false, false, false, false,
                true, false, false, false, false);

        when(userEmailNotificationRepository.findNotificationsByUser(TEST_EMAIL)).thenReturn(SUBSCRIPTION_LIST);
        when(userEmailNotificationMapper.toEntity(dailyMessageCountsDto)).thenReturn(dailyMessageCounts);
        when(userEmailNotificationMapper.toEntity(accessRequestDto)).thenReturn(accessRequests);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(java.util.Optional.of(TEST_USER));
        when(emailTypeRepository.findByEmailType("Daily Message Counts"))
                .thenReturn(java.util.Optional.of(SUBSCRIPTION_LIST.get(2).getEmailType()));
        when(emailTypeRepository.findByEmailType("Access Requests"))
                .thenReturn(java.util.Optional.of(SUBSCRIPTION_LIST.get(3).getEmailType()));

        Integer numModified = emailService.updateEmailSubscriptions(TEST_EMAIL,
                List.of(dailyMessageCountsDto, accessRequestDto));

        assertEquals(2, numModified);
        verify(userEmailNotificationRepository).findNotificationsByUser(TEST_EMAIL);
        verify(userEmailNotificationRepository, never()).deleteAll(anyList());
        verify(userEmailNotificationRepository, times(1)).saveAll(List.of(dailyMessageCounts, accessRequests));
    }

    @Test
    void testUpdateEmailSubscriptions_RemoveSubscriptions() {

        List<UserEmailNotification> SUBSCRIPTION_LIST = Arrays.asList(
                createUserEmailNotification(
                        "Support Requests", "Receive support requests from users", "user",
                        true, false, false, false, false,
                        true, false, false, false, false),
                createUserEmailNotification(
                        "Intersection Notification Summary",
                        "Receive automated intersection notification summary emails",
                        "user",
                        true, false, false, false, false,
                        true, true, true, true, true),
                createUserEmailNotification(
                        "Daily Message Counts", "Receive automated daily message count emails", "user",
                        false, false, false, false, false,
                        true, false, false, false, false),
                createUserEmailNotification(
                        "Access Requests", "Receive organization access requests from users", "admin",
                        false, false, false, false, false,
                        true, false, false, false, false));

        UserEmailNotificationDto supportRequestsDto = new UserEmailNotificationDto(
                "Support Requests", "Receive support requests from users", "admin",
                false, false, false, false, false,
                true, false, false, false, false);
        UserEmailNotificationDto intersectionNotificationSummaryDto = new UserEmailNotificationDto(
                "Intersection Notification Summary", "Receive automated intersection notification summary emails",
                "user",
                false, false, false, false, false,
                true, true, true, true, true);

        UserEmailNotification supportRequests = createUserEmailNotification(
                "Support Requests", "Receive support requests from users", "admin",
                true, false, false, false, false,
                true, false, false, false, false);

        UserEmailNotification intersectionNotificationSummaries = createUserEmailNotification(
                "Intersection Notification Summary", "Receive automated intersection notification summary emails",
                "admin",
                true, false, false, false, false,
                true, true, true, true, true);

        when(userEmailNotificationRepository.findNotificationsByUser(TEST_EMAIL)).thenReturn(SUBSCRIPTION_LIST);
        when(userEmailNotificationMapper.toEntity(supportRequestsDto)).thenReturn(supportRequests);
        when(userEmailNotificationMapper.toEntity(intersectionNotificationSummaryDto))
                .thenReturn(intersectionNotificationSummaries);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(java.util.Optional.of(TEST_USER));
        when(emailTypeRepository.findByEmailType("Support Requests"))
                .thenReturn(java.util.Optional.of(SUBSCRIPTION_LIST.get(0).getEmailType()));
        when(emailTypeRepository.findByEmailType("Intersection Notification Summary"))
                .thenReturn(java.util.Optional.of(SUBSCRIPTION_LIST.get(1).getEmailType()));

        Integer numModified = emailService.updateEmailSubscriptions(TEST_EMAIL,
                List.of(supportRequestsDto, intersectionNotificationSummaryDto));

        assertEquals(2, numModified);
        verify(userEmailNotificationRepository).findNotificationsByUser(TEST_EMAIL);
        verify(userEmailNotificationRepository, times(1))
                .deleteAll(List.of(supportRequests, intersectionNotificationSummaries));
        verify(userEmailNotificationRepository, never()).saveAll(anyList());
    }

    @Test
    void testUpdateEmailSubscriptions_UpdateSubscriptions() {

        List<UserEmailNotification> SUBSCRIPTION_LIST = Arrays.asList(
                createUserEmailNotification(
                        "Support Requests", "Receive support requests from users", "user",
                        true, false, false, false, false,
                        true, false, false, false, false),
                createUserEmailNotification(
                        "Intersection Notification Summary",
                        "Receive automated intersection notification summary emails",
                        "user",
                        true, false, false, false, false,
                        true, true, true, true, true),
                createUserEmailNotification(
                        "Daily Message Counts", "Receive automated daily message count emails", "user",
                        false, false, false, false, false,
                        true, false, false, false, false),
                createUserEmailNotification(
                        "Access Requests", "Receive organization access requests from users", "admin",
                        false, false, false, false, false,
                        true, false, false, false, false));

        UserEmailNotificationDto supportRequestsDto = new UserEmailNotificationDto(
                "Support Requests", "Receive support requests from users", "admin",
                true, true, false, false, false,
                true, false, false, false, false);
        UserEmailNotificationDto intersectionNotificationSummaryDto = new UserEmailNotificationDto(
                "Intersection Notification Summary", "Receive automated intersection notification summary emails",
                "user",
                true, false, true, false, false,
                true, true, true, true, true);

        UserEmailNotification supportRequests = createUserEmailNotification(
                "Support Requests", "Receive support requests from users", "admin",
                true, true, false, false, false,
                true, true, false, false, false);

        UserEmailNotification intersectionNotificationSummaries = createUserEmailNotification(
                "Intersection Notification Summary", "Receive automated intersection notification summary emails",
                "admin",
                true, false, true, false, false,
                true, true, true, true, true);

        when(userEmailNotificationRepository.findNotificationsByUser(TEST_EMAIL)).thenReturn(SUBSCRIPTION_LIST);
        when(userEmailNotificationMapper.toEntity(supportRequestsDto)).thenReturn(supportRequests);
        when(userEmailNotificationMapper.toEntity(intersectionNotificationSummaryDto))
                .thenReturn(intersectionNotificationSummaries);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(java.util.Optional.of(TEST_USER));
        when(emailTypeRepository.findByEmailType("Support Requests"))
                .thenReturn(java.util.Optional.of(SUBSCRIPTION_LIST.get(0).getEmailType()));
        when(emailTypeRepository.findByEmailType("Intersection Notification Summary"))
                .thenReturn(java.util.Optional.of(SUBSCRIPTION_LIST.get(1).getEmailType()));

        Integer numModified = emailService.updateEmailSubscriptions(TEST_EMAIL,
                List.of(supportRequestsDto, intersectionNotificationSummaryDto));

        assertEquals(2, numModified);
        verify(userEmailNotificationRepository).findNotificationsByUser(TEST_EMAIL);
        verify(userEmailNotificationRepository, never()).deleteAll(anyList());
        verify(userEmailNotificationRepository, times(1))
                .saveAll(List.of(supportRequests, intersectionNotificationSummaries));
    }

    @Test
    void testUpdateEmailSubscriptions_AllUpdateTypes() {

        List<UserEmailNotification> SUBSCRIPTION_LIST = Arrays.asList(
                createUserEmailNotification(
                        "Support Requests", "Receive support requests from users", "user",
                        true, false, false, false, false,
                        true, false, false, false, false),
                createUserEmailNotification(
                        "Intersection Notification Summary",
                        "Receive automated intersection notification summary emails",
                        "user",
                        true, false, false, false, false,
                        true, true, true, true, true),
                createUserEmailNotification(
                        "Daily Message Counts", "Receive automated daily message count emails", "user",
                        false, false, false, false, false,
                        true, false, false, false, false),
                createUserEmailNotification(
                        "Access Requests", "Receive organization access requests from users", "admin",
                        false, false, false, false, false,
                        true, false, false, false, false));

        UserEmailNotificationDto supportRequestsDto = new UserEmailNotificationDto(
                "Support Requests", "Receive support requests from users", "admin",
                true, true, false, false, false,
                true, false, false, false, false);
        UserEmailNotificationDto intersectionNotificationSummaryDto = new UserEmailNotificationDto(
                "Intersection Notification Summary", "Receive automated intersection notification summary emails",
                "user",
                false, false, false, false, false,
                true, true, true, true, true);
        UserEmailNotificationDto dailyMessageCountsDto = new UserEmailNotificationDto(
                "Daily Message Counts", "Receive automated daily message count emails", "user",
                true, false, false, false, false,
                true, false, false, false, false);
        UserEmailNotificationDto accessRequestDto = new UserEmailNotificationDto(
                "Access Requests", "Receive organization access requests from users", "admin",
                false, false, false, false, false,
                true, false, false, false, false);

        UserEmailNotification supportRequests = createUserEmailNotification(
                "Support Requests", "Receive support requests from users", "admin",
                true, true, false, false, false,
                true, true, false, false, false);
        UserEmailNotification intersectionNotificationSummaries = createUserEmailNotification(
                "Intersection Notification Summary", "Receive automated intersection notification summary emails",
                "user",
                true, false, false, false, false,
                true, true, true, true, true);
        UserEmailNotification dailyMessageCounts = createUserEmailNotification(
                "Daily Message Counts", "Receive automated daily message count emails", "user",
                false, false, false, false, false,
                true, false, false, false, false);
        UserEmailNotification accessRequests = createUserEmailNotification(
                "Access Requests", "Receive organization access requests from users", "admin",
                false, false, false, false, false,
                true, false, false, false, false);

        when(userEmailNotificationRepository.findNotificationsByUser(TEST_EMAIL)).thenReturn(SUBSCRIPTION_LIST);
        when(userEmailNotificationMapper.toEntity(supportRequestsDto)).thenReturn(supportRequests);
        when(userEmailNotificationMapper.toEntity(intersectionNotificationSummaryDto))
                .thenReturn(intersectionNotificationSummaries);
        when(userEmailNotificationMapper.toEntity(dailyMessageCountsDto)).thenReturn(dailyMessageCounts);
        when(userEmailNotificationMapper.toEntity(accessRequestDto)).thenReturn(accessRequests);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(java.util.Optional.of(TEST_USER));
        when(emailTypeRepository.findByEmailType("Support Requests"))
                .thenReturn(java.util.Optional.of(SUBSCRIPTION_LIST.get(0).getEmailType()));
        when(emailTypeRepository.findByEmailType("Intersection Notification Summary"))
                .thenReturn(java.util.Optional.of(SUBSCRIPTION_LIST.get(1).getEmailType()));
        when(emailTypeRepository.findByEmailType("Daily Message Counts"))
                .thenReturn(java.util.Optional.of(SUBSCRIPTION_LIST.get(2).getEmailType()));
        when(emailTypeRepository.findByEmailType("Access Requests"))
                .thenReturn(java.util.Optional.of(SUBSCRIPTION_LIST.get(3).getEmailType()));

        Integer numModified = emailService.updateEmailSubscriptions(TEST_EMAIL,
                List.of(supportRequestsDto, intersectionNotificationSummaryDto, dailyMessageCountsDto,
                        accessRequestDto));

        assertEquals(3, numModified);
        verify(userEmailNotificationRepository).findNotificationsByUser(TEST_EMAIL);
        verify(userEmailNotificationRepository, times(1)).deleteAll(List.of(intersectionNotificationSummaries));
        verify(userEmailNotificationRepository, times(1)).saveAll(List.of(supportRequests));
        verify(userEmailNotificationRepository, times(1)).saveAll(List.of(dailyMessageCounts));
    }

    // @Test
    // void testGetEmailSubscriptions_UserWithSubscriptions() {

    // when(postgresService.getEmailSubscriptionTypes()).thenReturn(SUBSCRIPTION_LIST);
    // when(postgresService.getEmailSubscriptionsByUser(TEST_EMAIL)).thenReturn(SUBSCRIPTION_LIST);

    // List<UserEmailNotificationDto> subscriptions =
    // emailService.getAllEmailSubscriptionOptionsForUser(TEST_EMAIL);

    // assertNotNull(subscriptions);
    // assertEquals(6, subscriptions.size());

    // assertEquals(SUBSCRIPTION_LIST, subscriptions);

    // verify(postgresService).getEmailSubscriptionTypes();
    // verify(postgresService).getEmailSubscriptionsByUser(TEST_EMAIL);
    // }
}