package us.dot.its.jpo.ode.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.emails.EmailCategory;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.EmailFrequency;
import us.dot.its.jpo.ode.api.models.emails.EmailRecipient;
import us.dot.its.jpo.ode.api.models.emails.EmailSendResponse;
import us.dot.its.jpo.ode.api.models.emails.UserEmailNotificationDto;
import us.dot.its.jpo.ode.api.models.emails.contents.ApiErrorEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.FirmwareUpgradeFailureEmailContents;
import us.dot.its.jpo.ode.api.models.postgres.tables.EmailType;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.User;
import us.dot.its.jpo.ode.api.models.postgres.tables.UserEmailNotification;
import us.dot.its.jpo.ode.api.repositories.EmailTypeRepository;
import us.dot.its.jpo.ode.api.models.emails.contents.RsuErrorSummaryEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.SupportRequestEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.message_counts.MessageCountEmailContents;
import us.dot.its.jpo.ode.api.emails.generators.ApiErrorEmailGenerator;
import us.dot.its.jpo.ode.api.emails.generators.FirmwareUpgradeFailureEmailGenerator;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.UserEmailNotificationRepository;
import us.dot.its.jpo.ode.api.repositories.UserRepository;
import us.dot.its.jpo.ode.api.emails.generators.MessageCountEmailGenerator;
import us.dot.its.jpo.ode.api.emails.generators.RsuErrorSummaryEmailGenerator;
import us.dot.its.jpo.ode.api.emails.generators.SupportRequestEmailGenerator;
import us.dot.its.jpo.ode.api.emails.providers.EmailProvider;
import us.dot.its.jpo.ode.api.mappers.UserEmailNotificationMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final EmailProvider emailProvider;
    private final EmailTypeRepository emailTypeRepository;
    private final UserEmailNotificationMapper userEmailNotificationMapper;
    private final UserEmailNotificationRepository userEmailNotificationRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PermissionService permissionService;
    private final SupportRequestEmailGenerator supportRequestEmailGenerator;
    private final MessageCountEmailGenerator messageCountEmailGenerator;
    private final FirmwareUpgradeFailureEmailGenerator firmwareUpgradeFailureEmailGenerator;
    private final ApiErrorEmailGenerator apiErrorEmailGenerator;
    private final RsuErrorSummaryEmailGenerator rsuErrorSummaryEmailGenerator;

    public void sendEmails(List<EmailRecipient> recipients, EmailContent content) {
        emailProvider.sendBatchedEmails(recipients, content);
    }

    public List<EmailRecipient> getUsersForNotificationType(EmailCategory category, EmailFrequency frequency) {
        return userEmailNotificationRepository
                .findUsersByNotificationType(category.getCategoryKey(), frequency.toQueryValue()).stream()
                .map(email -> new EmailRecipient(email, null))
                .toList();
    }

    public List<EmailRecipient> getUsersForNotificationTypeByRsu(EmailCategory category, String rsuIp,
            EmailFrequency frequency) {
        return userEmailNotificationRepository
                        .findUsersByNotificationTypeAndRsu(category.getCategoryKey(), frequency.toQueryValue(),
                        RsuManagementService.toIpv4Address(rsuIp))
                .stream()
                .map(email -> new EmailRecipient(email, null))
                .toList();
    }

    public List<EmailRecipient> getUsersForNotificationTypeByOrganization(EmailCategory category, String orgName,
            EmailFrequency frequency) {
        return userEmailNotificationRepository
                .findUsersByNotificationTypeAndOrganization(category.getCategoryKey(), frequency.toQueryValue(),
                        orgName)
                .stream()
                .map(email -> new EmailRecipient(email, null))
                .toList();
    }

    public List<EmailSendResponse> sendSupportRequest(SupportRequestEmailContents data) {
        EmailContent content = supportRequestEmailGenerator.generateEmailBody(data);
        List<EmailRecipient> recipients = getUsersForNotificationType(EmailCategory.SUPPORT_REQUEST,
                EmailFrequency.IMMEDIATE);
        if (recipients.isEmpty()) {
            log.warn("No recipients found for support request email");
            return Collections.emptyList();
        }
        return emailProvider.sendBatchedEmails(recipients, content);
    }

    public List<EmailSendResponse> sendMessageCounts(MessageCountEmailContents data) {
        EmailContent content = messageCountEmailGenerator.generateEmailBody(data);
        List<EmailRecipient> recipients = getUsersForNotificationTypeByOrganization(EmailCategory.MESSAGE_COUNTS,
                data.getOrganizationName(), EmailFrequency.ONCE_PER_DAY);
        if (recipients.isEmpty()) {
            log.warn("No recipients found for message count email for organization: {}", data.getOrganizationName());
            return Collections.emptyList();
        }
        return emailProvider.sendBatchedEmails(recipients, content);
    }

    public List<EmailSendResponse> sendFirmwareUpgradeFailure(FirmwareUpgradeFailureEmailContents data) {
        EmailContent content = firmwareUpgradeFailureEmailGenerator.generateEmailBody(data);
        List<EmailRecipient> recipients = getUsersForNotificationTypeByRsu(EmailCategory.FIRMWARE_UPGRADE_FAILURE,
                data.getRsuIp(), EmailFrequency.IMMEDIATE);
        if (recipients.isEmpty()) {
            log.warn("No recipients found for firmware upgrade failure email for RSU IP: {}", data.getRsuIp());
            return Collections.emptyList();
        }
        return emailProvider.sendBatchedEmails(recipients, content);
    }

    public List<EmailSendResponse> sendApiError(ApiErrorEmailContents data) {
        EmailContent content = apiErrorEmailGenerator.generateEmailBody(data);
        List<EmailRecipient> recipients = getUsersForNotificationType(EmailCategory.CRITICAL_ERROR_MESSAGE,
                EmailFrequency.IMMEDIATE);
        if (recipients.isEmpty()) {
            log.warn("No recipients found for API error email");
            return Collections.emptyList();
        }
        return emailProvider.sendBatchedEmails(recipients, content);
    }

    public List<EmailSendResponse> sendRsuErrorSummary(RsuErrorSummaryEmailContents data) {
        EmailContent content = rsuErrorSummaryEmailGenerator.generateEmailBody(data);
        String email = permissionService.getCvManagerAuthToken().getEmail();
        if (email == null || email.isBlank()) {
            log.warn("Unable to send RSU error summary: authenticated user token does not contain a valid email");
            return Collections.emptyList();
        }
        List<EmailRecipient> recipients = List.of(new EmailRecipient(email, ""));
        return emailProvider.sendBatchedEmails(recipients, content);
    }

    /**
     * Lists every subscription option available to a user, populated from the
     * user's saved subscription when one exists:
     * <ul>
     * <li>one entry, with no organization, per system-wide email type the user's
     * highest role qualifies for</li>
     * <li>one entry per organization the user belongs to and organization-scoped
     * email type the user's role in that organization qualifies for</li>
     * </ul>
     *
     * @param userEmail   email of the user
     * @param orgRoles    the user's role in each of their organizations, keyed by
     *                    organization name
     * @param isSuperUser whether the user is a super user, who qualifies for every
     *                    system-wide email type
     */
    public List<UserEmailNotificationDto> getAllEmailSubscriptionOptionsForUser(String userEmail,
            Map<String, UserRole> orgRoles, boolean isSuperUser) {
        List<UserEmailNotification> userSubscriptions = userEmailNotificationRepository
                .findNotificationsByUser(userEmail);
        List<EmailType> allSubscriptionTypes = emailTypeRepository.findAll();
        List<UserEmailNotificationDto> options = new ArrayList<>();

        UserRole systemRole = getSystemRole(orgRoles, isSuperUser);
        for (EmailType subType : allSubscriptionTypes) {
            if (!subType.getOrganizationScoped() && isAuthorized(systemRole, subType)) {
                options.add(toSubscriptionOption(subType, null, userSubscriptions));
            }
        }

        for (Map.Entry<String, UserRole> orgRole : new TreeMap<>(orgRoles).entrySet()) {
            for (EmailType subType : allSubscriptionTypes) {
                if (subType.getOrganizationScoped() && isAuthorized(orgRole.getValue(), subType)) {
                    options.add(toSubscriptionOption(subType, orgRole.getKey(), userSubscriptions));
                }
            }
        }
        return options;
    }

    private UserEmailNotificationDto toSubscriptionOption(EmailType subType, String orgName,
            List<UserEmailNotification> userSubscriptions) {
        return userSubscriptions.stream()
                .filter(sub -> sub.getEmailType().getEmailType().equals(subType.getEmailType())
                        && Objects.equals(getOrganizationName(sub), orgName))
                .findFirst()
                .map(userEmailNotificationMapper::toDto)
                .orElseGet(() -> userEmailNotificationMapper.fromEmailType(subType, orgName));
    }

    /**
     * The role a user holds for system-wide email types: ADMIN for super users,
     * otherwise their highest role in any organization, or null when they belong
     * to none.
     */
    private static UserRole getSystemRole(Map<String, UserRole> orgRoles, boolean isSuperUser) {
        if (isSuperUser) {
            return UserRole.ADMIN;
        }
        return orgRoles.values().stream()
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private static boolean isAuthorized(UserRole memberRole, EmailType emailType) {
        UserRole requiredRole = UserRole.fromString(emailType.getRequiredRole().getName());
        return memberRole != null && memberRole.hasMinimumRole(requiredRole);
    }

    private static String getOrganizationName(UserEmailNotification subscription) {
        return subscription.getOrganization() == null ? null : subscription.getOrganization().getName();
    }

    private static boolean isSameSubscription(UserEmailNotificationDto dto, UserEmailNotification entity) {
        return dto.getCategory().equals(entity.getEmailType().getEmailType())
                && Objects.equals(dto.getOrganization(), getOrganizationName(entity));
    }

    private static List<UserEmailNotificationDto> filterValidSubscriptionsForUser(
            List<UserEmailNotificationDto> requestedSubscriptions, Map<String, UserRole> orgRoles,
            boolean isSuperUser, List<EmailType> validEmailTypes) {
        UserRole systemRole = getSystemRole(orgRoles, isSuperUser);
        List<UserEmailNotificationDto> list = new ArrayList<>();
        for (UserEmailNotificationDto requestedSubscription : requestedSubscriptions) {
            EmailType emailType = validEmailTypes.stream()
                    .filter(type -> type.getEmailType().equals(requestedSubscription.getCategory()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Invalid email category: " + requestedSubscription.getCategory()));

            String orgName = requestedSubscription.getOrganization();
            if (!emailType.getOrganizationScoped()) {
                if (orgName != null) {
                    throw new IllegalArgumentException("Email type " + emailType.getEmailType()
                            + " is system-wide and cannot be set for organization " + orgName);
                }
                if (!isAuthorized(systemRole, emailType)) {
                    throw new AccessDeniedException("User does not have required role "
                            + emailType.getRequiredRole().getName() + " for email type " + emailType.getEmailType());
                }
            } else {
                if (orgName == null) {
                    throw new IllegalArgumentException(
                            "Email type " + emailType.getEmailType() + " requires an organization");
                }
                if (!orgRoles.containsKey(orgName)) {
                    throw new AccessDeniedException("User is not a member of organization " + orgName);
                }
                if (!isAuthorized(orgRoles.get(orgName), emailType)) {
                    throw new AccessDeniedException("User does not have required role "
                            + emailType.getRequiredRole().getName() + " in organization " + orgName
                        + " for email type " + emailType.getEmailType());
                }
            }

            if ((requestedSubscription.getImmediate() && !emailType.getSupportsImmediate()) ||
                    (requestedSubscription.getHourly() && !emailType.getSupportsHourly()) ||
                    (requestedSubscription.getDaily() && !emailType.getSupportsDaily()) ||
                    (requestedSubscription.getWeekly() && !emailType.getSupportsWeekly()) ||
                    (requestedSubscription.getMonthly() && !emailType.getSupportsMonthly())) {
                throw new IllegalArgumentException(
                        "Invalid subscription frequency for email type " + emailType.getEmailType());
            }
            list.add(requestedSubscription);
        }
        return list;
    }

    /**
     * Applies the requested subscriptions for a user. Each requested entry is
     * identified by (category, organization), with no organization for
     * system-wide email types: subscribed entries are created or have their
     * frequencies updated, unsubscribed entries are removed, and subscriptions
     * not mentioned in the request are left unchanged.
     *
     * @param userEmail              email of the user
     * @param orgRoles               the user's role in each of their
     *                               organizations, keyed by organization name
     * @param isSuperUser            whether the user is a super user
     * @param requestedSubscriptions requested subscription states
     */
    public void updateEmailSubscriptions(String userEmail, Map<String, UserRole> orgRoles, boolean isSuperUser,
            List<UserEmailNotificationDto> requestedSubscriptions) {
        List<UserEmailNotification> currentSubscriptions = userEmailNotificationRepository
                .findNotificationsByUser(userEmail);
        List<EmailType> allEmailTypes = emailTypeRepository.findAll();
        User user = userRepository.findByEmail(userEmail);
        List<UserEmailNotificationDto> validRequestedSubscriptions = filterValidSubscriptionsForUser(
                requestedSubscriptions, orgRoles, isSuperUser, allEmailTypes);

        List<UserEmailNotificationDto> subscriptionsToAdd = validRequestedSubscriptions.stream()
                .filter(sub -> sub.getSubscribed())
                .filter(sub -> currentSubscriptions.stream()
                        .noneMatch(currentSub -> isSameSubscription(sub, currentSub)))
                .toList();
        List<String> organizationNamesToAdd = subscriptionsToAdd.stream()
                .map(UserEmailNotificationDto::getOrganization)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, Organization> organizationsByName = organizationNamesToAdd.isEmpty() ? Map.of()
                : organizationRepository.findByNameIn(organizationNamesToAdd)
                .stream()
                .collect(Collectors.toMap(Organization::getName, org -> org));

        List<UserEmailNotification> addedSubscriptions = subscriptionsToAdd.stream()
                .map(subDto -> {
                    EmailType emailType = allEmailTypes.stream()
                            .filter(type -> type.getEmailType().equals(subDto.getCategory()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Invalid email category: " + subDto.getCategory()));
                    Organization organization = subDto.getOrganization() == null ? null
                            : Optional.ofNullable(organizationsByName.get(subDto.getOrganization()))
                                    .orElseThrow(() -> new IllegalArgumentException(
                                            "Invalid organization: " + subDto.getOrganization()));
                    return userEmailNotificationMapper.toEntity(subDto, user, emailType, organization);
                })
                .toList();

        List<UserEmailNotification> modifiedSubscriptions = currentSubscriptions.stream()
                .map(sub -> {
                    UserEmailNotificationDto requestedSub = validRequestedSubscriptions.stream()
                            .filter(reqSub -> reqSub.getSubscribed() && isSameSubscription(reqSub, sub))
                            .findFirst()
                            .orElse(null);
                    if (requestedSub != null && !requestedSub.isFrequencyEqual(sub)) {
                        userEmailNotificationMapper.updateFrequency(requestedSub, sub);
                        return sub;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();

        List<UserEmailNotification> removedSubscriptions = currentSubscriptions.stream()
                .filter(sub -> validRequestedSubscriptions.stream()
                        .anyMatch(reqSub -> isSameSubscription(reqSub, sub) && !reqSub.getSubscribed()))
                .toList();

        // Remove subscriptions that are no longer subscribed
        if (!removedSubscriptions.isEmpty()) {
            userEmailNotificationRepository.deleteAll(removedSubscriptions);
        }

        // Add new and update subscriptions that have modified frequencies
        if (!modifiedSubscriptions.isEmpty() || !addedSubscriptions.isEmpty()) {
            var subsToSave = new ArrayList<>(modifiedSubscriptions);
            subsToSave.addAll(addedSubscriptions);
            userEmailNotificationRepository.saveAll(subsToSave);
        }

        return;
    }
}