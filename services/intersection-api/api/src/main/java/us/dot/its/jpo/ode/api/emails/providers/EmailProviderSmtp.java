package us.dot.its.jpo.ode.api.emails.providers;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.EmailRecipient;
import us.dot.its.jpo.ode.api.models.emails.EmailSendResponse;

@Slf4j
@Component
@Qualifier("SMTP")
@Primary
@RequiredArgsConstructor
public class EmailProviderSmtp implements EmailProvider {

    private final EmailProperties emailProperties;
    private final UnsubscribeTokenGenerator unsubscribeTokenGenerator;
    private final JavaMailSender mailSender;

    @Override
    public EmailSendResponse sendEmail(EmailRecipient recipient, EmailContent content) {
        try {
            SimpleMailMessage message = getMessage(recipient, content);
            mailSender.send(message);
            return new EmailSendResponse(200, "Email sent successfully");
        } catch (org.springframework.mail.MailAuthenticationException e) {
            log.error("SMTP authentication failed for recipient {}: {}", recipient.getEmail(), e.getMessage());
            return new EmailSendResponse(500, "SMTP authentication failed");
        } catch (org.springframework.mail.MailSendException e) {
            log.error("Failed to send email to {}: {}", recipient.getEmail(), e.getMessage());
            return new EmailSendResponse(500, "Failed to send email: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error sending email to {}: {}", recipient.getEmail(), e.getMessage());
            return new EmailSendResponse(500, "Unknown error: " + e.getMessage());
        }
    }

    @Override
    public List<EmailSendResponse> sendBatchedEmails(List<EmailRecipient> recipients, EmailContent content) {
        try {
            List<SimpleMailMessage> messages = recipients.stream().map(r -> getMessage(r, content)).toList();
            mailSender.send(messages.toArray(new SimpleMailMessage[0]));
            return List.of(new EmailSendResponse(200, "Emails sent successfully"));
        } catch (org.springframework.mail.MailAuthenticationException e) {
            log.error("SMTP authentication failed for batch: {}", e.getMessage());
            return List.of(new EmailSendResponse(500, "SMTP authentication failed"));
        } catch (org.springframework.mail.MailSendException e) {
            log.error("Failed to send batch emails: {}", e.getMessage());
            return List.of(new EmailSendResponse(500, "Failed to send batch emails: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error sending batch emails: {}", e.getMessage());
            return List.of(new EmailSendResponse(500, "Unknown error: " + e.getMessage()));
        }
    }

    private SimpleMailMessage getMessage(EmailRecipient recipient, EmailContent content) {
        String unsubscribeUrl = getUnsubscribeUrl(recipient.getEmail());
        String htmlText = replacePlaceholders(content.getBody(), unsubscribeUrl);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailProperties.getSenderAddress());
        message.setTo(recipient.getEmail());
        message.setSubject(content.getSubject());
        message.setText(htmlText);

        return message;
    }

    private String replacePlaceholders(String htmlContents, String unsubscribeUrl) {
        return htmlContents.replaceAll("\\{\\{unsubscribe_url\\}\\}", unsubscribeUrl);
    }

    private String getUnsubscribeUrl(String email) {
        return unsubscribeTokenGenerator.generateUnsubscribeUrl(email);
    }
}
