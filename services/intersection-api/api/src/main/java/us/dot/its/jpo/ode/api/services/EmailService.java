package us.dot.its.jpo.ode.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import org.springframework.stereotype.Service;

import us.dot.its.jpo.ode.api.models.emails.EmailCategory;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.EmailFrequency;
import us.dot.its.jpo.ode.api.models.emails.EmailRecipient;
import us.dot.its.jpo.ode.api.models.emails.EmailSendResponse;
import us.dot.its.jpo.ode.api.models.emails.contents.IntersectionNotificationSummaryEmailContents;
import us.dot.its.jpo.ode.api.models.postgres.derived.EmailSubscription;
import us.dot.its.jpo.ode.api.emails.generators.IntersectionNotificationSummaryEmailGenerator;
import us.dot.its.jpo.ode.api.emails.providers.EmailProvider;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailProvider emailProvider;
    private final PostgresService postgresService;
    private final IntersectionNotificationSummaryEmailGenerator intersectionNotificationSummaryEmailGenerator;

    public void sendEmails(List<EmailRecipient> recipients, EmailContent content) {
        emailProvider.sendBatchedEmails(recipients, content);
    }

    public List<EmailRecipient> getUsersForNotificationType(EmailCategory category, EmailFrequency frequency) {
        return postgresService.getUsersByNotificationType(category.getCategoryKey(), frequency).stream()
                .map(email -> new EmailRecipient(email, null))
                .toList();
    }

    public List<EmailRecipient> getUsersForNotificationTypeByRsu(EmailCategory category, String rsuIp,
            EmailFrequency frequency) {
        return postgresService.getUsersByNotificationTypeAndRsu(category.getCategoryKey(), rsuIp, frequency).stream()
                .map(email -> new EmailRecipient(email, null))
                .toList();
    }

    public List<EmailRecipient> getUsersForNotificationTypeByOrganization(EmailCategory category, String orgName,
            EmailFrequency frequency) {
        return postgresService.getUsersByNotificationTypeAndOrganization(category.getCategoryKey(), orgName, frequency)
                .stream()
                .map(email -> new EmailRecipient(email, null))
                .toList();
    }

    public List<EmailSendResponse> sendIntersectionNotificationSummaryEmailSendResponses(
            IntersectionNotificationSummaryEmailContents data) {
        EmailContent content = intersectionNotificationSummaryEmailGenerator.generateEmailBody(data);
        List<EmailRecipient> recipients = getUsersForNotificationType(EmailCategory.INTERSECTION_NOTIFICATION_SUMMARY,
                EmailFrequency.IMMEDIATE);
        return emailProvider.sendBatchedEmails(recipients, content);
    }

    // TODO: Filter by user's role and required role
    public List<EmailSubscription> getAllEmailSubscriptionOptionsForUser(String userEmail) {
        List<EmailSubscription> userSubscriptions = postgresService.getEmailSubscriptionsByUser(userEmail);
        List<EmailSubscription> allSubscriptionTypes = postgresService.getEmailSubscriptionTypes();
        return allSubscriptionTypes.stream().map(subType -> {
            for (EmailSubscription subscribedType : userSubscriptions) {
                if (subscribedType.getCategory().equals(subType.getCategory())) {
                    return subscribedType;
                }
            }
            return subType;
        }).toList();
    }

    public int updateEmailSubscriptions(String userEmail, List<EmailSubscription> requestedSubscriptions) {
        List<EmailSubscription> currentSubscriptions = postgresService.getEmailSubscriptionsByUser(userEmail).stream()
                .filter(sub -> sub.getSubscribed()).toList();
        List<String> addedSubscriptions = requestedSubscriptions.stream()
                .filter(sub -> sub.getSubscribed())
                .filter(sub -> currentSubscriptions.stream()
                        .noneMatch(currentSub -> currentSub.getCategory().equals(sub.getCategory())))
                .map(EmailSubscription::getCategory)
                .toList();

        List<EmailSubscription> modifiedSubscriptions = requestedSubscriptions.stream()
                .filter(sub -> sub.getSubscribed())
                .filter(sub -> currentSubscriptions.stream()
                        .anyMatch(currentSub -> currentSub.getCategory().equals(sub.getCategory())
                                && !currentSub.isFrequencyEqual(sub)))
                .toList();

        List<String> removedSubscriptions = requestedSubscriptions.stream()
                .filter(sub -> !sub.getSubscribed())
                .filter(sub -> currentSubscriptions.stream()
                        .anyMatch(currentSub -> currentSub.getCategory().equals(sub.getCategory())))
                .map(EmailSubscription::getCategory)
                .toList();

        int numModified = removedSubscriptions.size() + modifiedSubscriptions.size() + addedSubscriptions.size();
        if (!removedSubscriptions.isEmpty()) {
            postgresService.removeEmailSubscriptionsByUser(userEmail, removedSubscriptions);
        }
        modifiedSubscriptions.forEach(subType -> {
            postgresService.updateEmailSubscriptionByUser(userEmail, subType);
        });
        addedSubscriptions.forEach(subType -> {
            postgresService.addEmailSubscriptionByUser(userEmail, subType);
        });

        return numModified;
    }
}