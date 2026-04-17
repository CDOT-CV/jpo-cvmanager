package us.dot.its.jpo.ode.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import us.dot.its.jpo.ode.api.models.emails.EmailCategory;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.EmailFrequency;
import us.dot.its.jpo.ode.api.models.emails.EmailRecipient;
import us.dot.its.jpo.ode.api.models.emails.EmailSendResponse;
import us.dot.its.jpo.ode.api.models.emails.UserEmailNotificationDto;
import us.dot.its.jpo.ode.api.models.emails.contents.IntersectionNotificationSummaryEmailContents;
import us.dot.its.jpo.ode.api.models.postgres.tables.EmailType;
import us.dot.its.jpo.ode.api.models.postgres.tables.User;
import us.dot.its.jpo.ode.api.models.postgres.tables.UserEmailNotification;
import us.dot.its.jpo.ode.api.repositories.EmailTypeRepository;
import us.dot.its.jpo.ode.api.repositories.UserEmailNotificationRepository;
import us.dot.its.jpo.ode.api.repositories.UserRepository;
import us.dot.its.jpo.ode.api.emails.generators.IntersectionNotificationSummaryEmailGenerator;
import us.dot.its.jpo.ode.api.emails.providers.EmailProvider;
import us.dot.its.jpo.ode.api.mappers.UserEmailNotificationMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final EmailProvider emailProvider;
    private final EmailTypeRepository emailTypeRepository;
    private final UserEmailNotificationMapper userEmailNotificationMapper;
    private final IntersectionNotificationSummaryEmailGenerator intersectionNotificationSummaryEmailGenerator;
    private final UserEmailNotificationRepository userEmailNotificationRepository;
    private final UserRepository userRepository;

    public void sendEmails(List<EmailRecipient> recipients, EmailContent content) {
        emailProvider.sendBatchedEmails(recipients, content);
    }

    public List<EmailRecipient> getUsersForNotificationType(EmailCategory category, EmailFrequency frequency) {
        return userEmailNotificationRepository
                .findUsersByNotificationType(category.getCategoryKey(), frequency.toString()).stream()
                .map(email -> new EmailRecipient(email, null))
                .toList();
    }

    public List<EmailRecipient> getUsersForNotificationTypeByRsu(EmailCategory category, String rsuIp,
            EmailFrequency frequency) {
        try {
            return userEmailNotificationRepository
                    .findUsersByNotificationTypeAndRsu(category.getCategoryKey(), frequency.toString(),
                            InetAddress.getByName(rsuIp))
                    .stream()
                    .map(email -> new EmailRecipient(email, null))
                    .toList();
        } catch (UnknownHostException e) {
            log.error("Invalid RSU IP address: {}", rsuIp, e);
            return Collections.emptyList();
        }
    }

    public List<EmailRecipient> getUsersForNotificationTypeByOrganization(EmailCategory category, String orgName,
            EmailFrequency frequency) {
        return userEmailNotificationRepository
                .findUsersByNotificationTypeAndOrganization(category.getCategoryKey(), frequency.toString(), orgName)
                .stream()
                .map(email -> new EmailRecipient(email, null))
                .toList();
    }

    public List<EmailSendResponse> sendIntersectionNotificationSummaryEmailSendResponses(
            IntersectionNotificationSummaryEmailContents data) {
        EmailContent content = intersectionNotificationSummaryEmailGenerator.generateEmailBody(data);
        List<EmailRecipient> recipients = getUsersForNotificationType(EmailCategory.INTERSECTION_NOTIFICATION_SUMMARY,
                EmailFrequency.IMMEDIATE);
        ArrayList<EmailRecipient> newRecipients = new ArrayList<>(recipients);
        return emailProvider.sendBatchedEmails(newRecipients, content);
    }

    // TODO: Filter by user's role and required role
    public List<UserEmailNotificationDto> getAllEmailSubscriptionOptionsForUser(String userEmail) {
        List<UserEmailNotification> userSubscriptions = userEmailNotificationRepository
                .findNotificationsByUser(userEmail);
        List<EmailType> allSubscriptionTypes = emailTypeRepository.findAll();
        return allSubscriptionTypes.stream().map(subType -> {
            for (UserEmailNotification subscribedType : userSubscriptions) {
                if (subscribedType.getEmailType().getEmailType().equals(subType.getEmailType())) {
                    return userEmailNotificationMapper.toDto(subscribedType);
                }
            }
            return userEmailNotificationMapper.fromEmailType(subType);
        }).toList();
    }

    public int updateEmailSubscriptions(String userEmail, List<UserEmailNotificationDto> requestedSubscriptions) {
        List<UserEmailNotification> currentSubscriptions = userEmailNotificationRepository
                .findNotificationsByUser(userEmail)
                .stream()
                .filter(sub -> sub.getSubscribed()).toList();
        List<UserEmailNotification> addedSubscriptions = requestedSubscriptions.stream()
                .filter(sub -> sub.getSubscribed())
                .filter(sub -> currentSubscriptions.stream()
                        .noneMatch(currentSub -> currentSub.getEmailType().getEmailType().equals(sub.getCategory())))
                .map(subDto -> {
                    UserEmailNotification sub = userEmailNotificationMapper.toEntity(subDto);
                    User user = userRepository.findByEmail(userEmail)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));
                    EmailType emailType = emailTypeRepository.findByEmailType(subDto.getCategory())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Email type not found: " + subDto.getCategory()));
                    sub.setUser(user);
                    sub.setEmailType(emailType);
                    return sub;
                })
                .toList();

        List<UserEmailNotification> modifiedSubscriptions = currentSubscriptions.stream()
                .map(sub -> {
                    UserEmailNotificationDto requestedSub = requestedSubscriptions.stream()
                            .filter(reqSub -> reqSub.getSubscribed()
                                    && reqSub.getCategory().equals(sub.getEmailType().getEmailType()))
                            .findFirst()
                            .orElse(null);
                    if (requestedSub != null && !sub.isFrequencyEqual(requestedSub)) {
                        sub.updateFrequency(requestedSub);
                        return sub;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();

        List<UserEmailNotification> removedSubscriptions = currentSubscriptions.stream()
                .filter(sub -> requestedSubscriptions.stream()
                        .anyMatch(reqSub -> reqSub.getCategory().equals(sub.getEmailType().getEmailType())
                                && !reqSub.getSubscribed()))
                .toList();

        int numModified = removedSubscriptions.size() + modifiedSubscriptions.size() + addedSubscriptions.size();

        // Remove subscriptions that are no longer subscribed
        if (!removedSubscriptions.isEmpty()) {
            userEmailNotificationRepository.deleteAll(removedSubscriptions);
        }

        // Update subscriptions that have modified frequencies
        if (!modifiedSubscriptions.isEmpty()) {
            userEmailNotificationRepository.saveAll(modifiedSubscriptions);
        }

        // Add new subscriptions
        if (!addedSubscriptions.isEmpty()) {
            userEmailNotificationRepository.saveAll(addedSubscriptions);
        }

        return numModified;
    }
}