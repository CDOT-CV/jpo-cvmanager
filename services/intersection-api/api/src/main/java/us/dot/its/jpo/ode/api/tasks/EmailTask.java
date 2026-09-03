package us.dot.its.jpo.ode.api.tasks;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.conflictmonitor.monitor.models.notifications.Notification;
import us.dot.its.jpo.ode.api.accessors.counts.CountsRepository;
import us.dot.its.jpo.ode.api.accessors.notifications.active_notification.ActiveNotificationRepository;
import us.dot.its.jpo.ode.api.emails.generators.IntersectionNotificationSummaryEmailGenerator;
import us.dot.its.jpo.ode.api.models.MessageCount;
import us.dot.its.jpo.ode.api.models.emails.EmailCategory;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.EmailFrequency;
import us.dot.its.jpo.ode.api.models.emails.EmailRecipient;
import us.dot.its.jpo.ode.api.models.emails.contents.IntersectionNotificationSummaryEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.message_counts.MessageCountCountsItem;
import us.dot.its.jpo.ode.api.models.emails.contents.message_counts.MessageCountEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.message_counts.MessageCountRsuItem;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.services.EmailService;

@Slf4j
@Component
@ConditionalOnProperty(name = "enable.email", havingValue = "true", matchIfMissing = false)
public class EmailTask {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    private static final int HOURLY_NOTIFICATION_EMAIL_RATE_MILLISECONDS = 60 * 60 * 1000; // 1 hour
    private static final String DAILY_NOTIFICATION_CRON = "0 0 0 * * ?"; // every day at midnight
    private static final String WEEKLY_NOTIFICATION_CRON = "0 0 0 * * 0"; // every sunday at midnight
    private static final String MONTHLY_NOTIFICATION_CRON = "0 0 0 1 * ?"; // first day of the month at midnight
    private static final String[] MESSAGE_TYPES = { "BSM", "TIM", "Map", "SPaT", "SRM", "SSM" };

    private final EmailService email;
    private final ActiveNotificationRepository activeNotificationRepo;
    private final IntersectionNotificationSummaryEmailGenerator emailGenerator;
    private final CountsRepository countsRepository;
    private final OrganizationRepository organizationRepository;
    private final String deploymentEnvironment;

    private List<Notification> lastHourList;
    private List<Notification> lastDayList;
    private List<Notification> lastWeekList;
    private List<Notification> lastMonthList;

    private final int maximumResponseSize;

    public EmailTask(EmailService email,
            ActiveNotificationRepository activeNotificationRepo,
            @Value("${maximumResponseSize}") int maximumResponseSize,
            IntersectionNotificationSummaryEmailGenerator emailGenerator,
            CountsRepository countsRepository,
            OrganizationRepository organizationRepository,
            @Value("${deployment.environment:UNKNOWN}") String deploymentEnvironment) {
        this.email = email;
        this.activeNotificationRepo = activeNotificationRepo;
        this.maximumResponseSize = maximumResponseSize;
        this.emailGenerator = emailGenerator;
        this.countsRepository = countsRepository;
        this.organizationRepository = organizationRepository;
        this.deploymentEnvironment = deploymentEnvironment;
    }

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("UTC"));

    @Scheduled(fixedRate = HOURLY_NOTIFICATION_EMAIL_RATE_MILLISECONDS)
    public void sendHourlyNotifications() {
        log.info("Checking Hourly Notifications: {}", dateFormat.format(new Date()));
        if (lastHourList == null) {
            lastHourList = getActiveNotifications();
            return;
        }

        List<Notification> currentNotifications = getActiveNotifications();

        List<Notification> newNotifications = getNewNotifications(currentNotifications, lastHourList);

        lastHourList = currentNotifications;

        if (!newNotifications.isEmpty()) {
            List<EmailRecipient> recipients = email.getUsersForNotificationType(
                    EmailCategory.INTERSECTION_NOTIFICATION_SUMMARY,
                    EmailFrequency.ONCE_PER_HOUR);
            if (!recipients.isEmpty()) {
                EmailContent content = emailGenerator
                        .generateEmailBody(new IntersectionNotificationSummaryEmailContents(newNotifications));
                email.sendEmails(recipients, content);
            }
        }
    }

    @Scheduled(cron = DAILY_NOTIFICATION_CRON)
    public void sendDailyNotifications() {
        log.info("Checking Daily Notifications: {}", dateFormat.format(new Date()));
        if (lastDayList == null) {
            lastDayList = getActiveNotifications();
            sendDailyCountEmails();
            return;
        }

        List<Notification> currentNotifications = getActiveNotifications();

        List<Notification> newNotifications = getNewNotifications(currentNotifications, lastDayList);

        lastDayList = currentNotifications;

        if (!newNotifications.isEmpty()) {
            List<EmailRecipient> recipients = email.getUsersForNotificationType(
                    EmailCategory.INTERSECTION_NOTIFICATION_SUMMARY,
                    EmailFrequency.ONCE_PER_DAY);
            if (!recipients.isEmpty()) {
                EmailContent content = emailGenerator
                        .generateEmailBody(new IntersectionNotificationSummaryEmailContents(newNotifications));
                email.sendEmails(recipients, content);
            }
        }

        sendDailyCountEmails();
    }

    @Scheduled(cron = WEEKLY_NOTIFICATION_CRON)
    public void sendWeeklyNotifications() {
        log.info("Checking Weekly Notifications: {}", dateFormat.format(new Date()));
        if (lastWeekList == null) {
            lastWeekList = getActiveNotifications();
            return;
        }

        List<Notification> currentNotifications = getActiveNotifications();

        List<Notification> newNotifications = getNewNotifications(currentNotifications, lastWeekList);

        lastWeekList = currentNotifications;

        if (!newNotifications.isEmpty()) {
            List<EmailRecipient> recipients = email.getUsersForNotificationType(
                    EmailCategory.INTERSECTION_NOTIFICATION_SUMMARY,
                    EmailFrequency.ONCE_PER_WEEK);
            if (!recipients.isEmpty()) {
                EmailContent content = emailGenerator
                        .generateEmailBody(new IntersectionNotificationSummaryEmailContents(newNotifications));
                email.sendEmails(recipients, content);
            }
        }
    }

    @Scheduled(cron = MONTHLY_NOTIFICATION_CRON)
    public void sendMonthlyNotifications() {
        log.info("Checking Monthly Notifications: {}", dateFormat.format(new Date()));
        if (lastMonthList == null) {
            lastMonthList = getActiveNotifications();
            return;
        }

        List<Notification> currentNotifications = getActiveNotifications();

        List<Notification> newNotifications = getNewNotifications(currentNotifications, lastMonthList);

        lastMonthList = currentNotifications;

        if (!newNotifications.isEmpty()) {
            List<EmailRecipient> recipients = email.getUsersForNotificationType(
                    EmailCategory.INTERSECTION_NOTIFICATION_SUMMARY,
                    EmailFrequency.ONCE_PER_MONTH);
            if (!recipients.isEmpty()) {
                EmailContent content = emailGenerator
                        .generateEmailBody(new IntersectionNotificationSummaryEmailContents(newNotifications));
                email.sendEmails(recipients, content);
            }
        }

    }

    public List<Notification> getActiveNotifications() {
        Page<Notification> notifications = activeNotificationRepo
                .find(null, null, null, PageRequest.of(0, maximumResponseSize));
        return notifications.getContent();
    }

    public List<Notification> getNewNotifications(List<Notification> newList, List<Notification> oldList) {

        List<Notification> newNotifications = new ArrayList<>();

        for (Notification newNotification : newList) {
            boolean found = false;
            for (Notification oldNotification : oldList) {
                if (newNotification.key.equals(oldNotification.key)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                newNotifications.add(newNotification);
            }
        }
        return newNotifications;
    }

    /**
     * Sends daily count emails for all organizations.
     * Isolated so errors do not affect other notification emails.
     */
    void sendDailyCountEmails() {
        try {
            log.info("Starting daily count email task");

            LocalDateTime endDateTime = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime startDateTime = endDateTime.minusDays(1);

            long startTimeMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endTimeMillis = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            log.info("Querying counts from {} to {}", startDateTime, endDateTime);

            List<String> organizations = organizationRepository.findAllOrganizationNames();
            if (organizations == null) {
                log.warn("Organization list was null; skipping daily count emails");
                return;
            }
            for (String orgName : organizations) {
                List<MessageCount> allCounts = new ArrayList<>();
                for (String messageType : MESSAGE_TYPES) {
                    List<MessageCount> counts = countsRepository.getRsuOrganizationMessageCounts(
                            orgName, messageType, startTimeMillis, endTimeMillis);
                    if (counts != null) {
                        allCounts.addAll(counts);
                    }
                }

                MessageCountEmailContents emailContents = toMessageCountEmailContents(
                        orgName, allCounts, startDateTime, endDateTime);
                if (emailContents.getRsuCounts().isEmpty()) {
                    log.warn("No RSU counts found for organization: {}", orgName);
                    continue;
                }

                email.sendMessageCounts(emailContents);
                log.info("Sent daily count emails for organization: {}", orgName);
            }

            log.info("Completed daily count email task");
        } catch (Exception e) {
            log.error("Error in daily count email task: {}", e.getMessage(), e);
        }
    }

    MessageCountEmailContents toMessageCountEmailContents(String orgName, List<MessageCount> allCounts,
            LocalDateTime startDateTime, LocalDateTime endDateTime) {
        Map<String, MessageCountRsuItem> rsuItems = new LinkedHashMap<>();

        for (MessageCount count : allCounts) {
            if (count == null || count.getRsuIp() == null) {
                continue;
            }
            String typeKey = canonicalMessageType(count.getMessageType());
            if (typeKey == null) {
                continue;
            }

            MessageCountRsuItem rsuItem = rsuItems.computeIfAbsent(count.getRsuIp(), ip -> {
                MessageCountRsuItem item = new MessageCountRsuItem();
                item.setRsuIp(ip);
                item.setPrimaryRoute(count.getRoad() != null ? count.getRoad() : "Unknown");
                item.setMessageCountsByType(new HashMap<>());
                return item;
            });

            MessageCountCountsItem countsItem = new MessageCountCountsItem();
            countsItem.setIn(count.getOdeInputCount() != null ? count.getOdeInputCount().intValue() : 0);
            countsItem.setOut(count.getOdeOutputCount() != null ? count.getOdeOutputCount().intValue() : 0);
            rsuItem.getMessageCountsByType().put(typeKey, countsItem);
        }

        for (MessageCountRsuItem item : rsuItems.values()) {
            for (String type : MESSAGE_TYPES) {
                item.getMessageCountsByType().putIfAbsent(type, emptyCountsItem());
            }
        }

        MessageCountEmailContents emailContents = new MessageCountEmailContents();
        emailContents.setOrganizationName(orgName);
        emailContents.setDeploymentTitle(
                String.format("%s Environment Counts - %s", deploymentEnvironment, orgName));
        emailContents.setStartDate(startDateTime.atZone(ZoneId.systemDefault()).toInstant());
        emailContents.setEndDate(endDateTime.atZone(ZoneId.systemDefault()).toInstant());
        emailContents.setMessageTypeList(List.of(MESSAGE_TYPES));
        emailContents.setRsuCounts(new ArrayList<>(rsuItems.values()));
        return emailContents;
    }

    private static MessageCountCountsItem emptyCountsItem() {
        MessageCountCountsItem item = new MessageCountCountsItem();
        item.setIn(0);
        item.setOut(0);
        return item;
    }

    private static String canonicalMessageType(String messageType) {
        if (messageType == null) {
            return null;
        }
        for (String type : MESSAGE_TYPES) {
            if (type.equalsIgnoreCase(messageType)) {
                return type;
            }
        }
        return messageType;
    }
}
