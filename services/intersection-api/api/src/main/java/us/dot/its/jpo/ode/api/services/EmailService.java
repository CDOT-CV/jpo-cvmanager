package us.dot.its.jpo.ode.api.services;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import us.dot.its.jpo.ode.api.models.emails.EmailCategory;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.EmailFrequency;
import us.dot.its.jpo.ode.api.models.emails.EmailRecipient;
import us.dot.its.jpo.ode.api.models.emails.EmailSendResponse;
import us.dot.its.jpo.ode.api.models.emails.contents.SupportRequestEmailContents;
import us.dot.its.jpo.ode.api.emails.generators.SupportRequestEmailGenerator;
import us.dot.its.jpo.ode.api.emails.providers.EmailProvider;

@Slf4j
@Service
public class EmailService {

    private final EmailProvider emailProvider;
    private final PostgresService postgresService;
    private final SupportRequestEmailGenerator supportRequestEmailGenerator;

    public EmailService(
            @Qualifier("${email.broker}") EmailProvider provider,
            PostgresService postgresService,
            SupportRequestEmailGenerator supportRequestEmailGenerator) {
        this.emailProvider = provider;
        this.postgresService = postgresService;
        this.supportRequestEmailGenerator = supportRequestEmailGenerator;
    }

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
}