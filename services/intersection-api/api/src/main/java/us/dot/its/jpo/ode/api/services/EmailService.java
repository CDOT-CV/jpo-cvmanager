package us.dot.its.jpo.ode.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import us.dot.its.jpo.ode.api.models.emails.EmailCategory;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.EmailFrequency;
import us.dot.its.jpo.ode.api.models.emails.EmailRecipient;
import us.dot.its.jpo.ode.api.models.emails.EmailSendResponse;
import us.dot.its.jpo.ode.api.models.emails.contents.FirmwareUpgradeFailureEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.RsuErrorSummaryEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.SupportRequestEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.access_requests.AccessRequestEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.message_counts.MessageCountEmailContents;
import us.dot.its.jpo.ode.api.emails.generators.AccessRequestEmailGenerator;
import us.dot.its.jpo.ode.api.emails.generators.FirmwareUpgradeFailureEmailGenerator;
import us.dot.its.jpo.ode.api.emails.generators.MessageCountEmailGenerator;
import us.dot.its.jpo.ode.api.emails.generators.RsuErrorSummaryEmailGenerator;
import us.dot.its.jpo.ode.api.emails.generators.SupportRequestEmailGenerator;
import us.dot.its.jpo.ode.api.emails.providers.EmailProvider;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    @Qualifier("${email.broker}")
    private final EmailProvider emailProvider;
    private final PostgresService postgresService;
    private final SupportRequestEmailGenerator supportRequestEmailGenerator;
    private final AccessRequestEmailGenerator accessRequestEmailGenerator;
    private final MessageCountEmailGenerator messageCountEmailGenerator;
    private final FirmwareUpgradeFailureEmailGenerator firmwareUpgradeFailureEmailGenerator;
    private final RsuErrorSummaryEmailGenerator rsuErrorSummaryEmailGenerator;

    public void sendEmails(List<EmailRecipient> recipients, EmailContent content) {
        emailProvider.sendBatchedEmails(recipients, content);
    }

    public List<EmailRecipient> getUsersForNotificationType(EmailCategory category, EmailFrequency frequency) {
        // TODO: Filter by email frequency
        return postgresService.getUsersByNotificationType(category.getCategoryKey()).stream()
                .map(email -> new EmailRecipient(email, null))
                .toList();
    }

    public List<String> getSupportRequestEmailList(String organization) {
        List<String> recipients = new ArrayList<>();
        if (organization != null) {
            recipients.addAll(postgresService.getOrganizationEmailListByRole(organization, "ADMIN"));
        }
        if (recipients.isEmpty()) {
            recipients.addAll(postgresService.getSuperUserEmailList());
        }
        return recipients;
    }

    public List<EmailSendResponse> sendSupportRequest(SupportRequestEmailContents data) {
        EmailContent content = supportRequestEmailGenerator.generateEmailBody(data);
        List<EmailRecipient> recipients = getUsersForNotificationType(EmailCategory.SUPPORT_REQUEST,
                EmailFrequency.ALWAYS);
        return emailProvider.sendBatchedEmails(recipients, content);
    }

    public List<EmailSendResponse> sendAccessRequest(AccessRequestEmailContents data) {
        EmailContent content = accessRequestEmailGenerator.generateEmailBody(data);
        List<EmailRecipient> recipients = getUsersForNotificationType(EmailCategory.ACCESS_REQUEST,
                EmailFrequency.ALWAYS);
        return emailProvider.sendBatchedEmails(recipients, content);
    }

    public List<EmailSendResponse> sendMessageCounts(MessageCountEmailContents data) {
        EmailContent content = messageCountEmailGenerator.generateEmailBody(data);
        List<EmailRecipient> recipients = getUsersForNotificationType(EmailCategory.MESSAGE_COUNTS,
                EmailFrequency.ALWAYS);
        return emailProvider.sendBatchedEmails(recipients, content);
    }

    public List<EmailSendResponse> sendFirmwareUpgradeFailure(FirmwareUpgradeFailureEmailContents data) {
        EmailContent content = firmwareUpgradeFailureEmailGenerator.generateEmailBody(data);
        // TODO: Use email addresses from RSU org only
        List<EmailRecipient> recipients = getUsersForNotificationType(EmailCategory.FIRMWARE_UPGRADE_FAILURE,
                EmailFrequency.ALWAYS);
        return emailProvider.sendBatchedEmails(recipients, content);
    }

    public List<EmailSendResponse> sendRsuErrorSummary(RsuErrorSummaryEmailContents data) {
        EmailContent content = rsuErrorSummaryEmailGenerator.generateEmailBody(data);
        // TODO: Use email addresses from RSU org only
        List<EmailRecipient> recipients = getUsersForNotificationType(EmailCategory.RSU_ERROR_SUMMARY,
                EmailFrequency.ALWAYS);
        return emailProvider.sendBatchedEmails(recipients, content);
    }
}