package us.dot.its.jpo.ode.api.emails.providers;

import java.util.List;
import java.util.ArrayList;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.EmailRecipient;
import us.dot.its.jpo.ode.api.models.emails.EmailSendResponse;

@Slf4j
@Component
@ConditionalOnProperty(name = "email.broker", havingValue = "SMTP", matchIfMissing = true)
@RequiredArgsConstructor
public class EmailProviderSmtp implements EmailProvider {

    private final EmailProperties emailProperties;
    private final UnsubscribeTokenGenerator unsubscribeTokenGenerator;
    private final JavaMailSender mailSender;

    @Override
    public List<EmailSendResponse> sendBatchedEmails(List<EmailRecipient> recipients, EmailContent content) {
        List<EmailSendResponse> responses = new ArrayList<>();

        try {
            log.info("Starting SMTP batch email send for {} recipients", recipients.size());

            for (EmailRecipient recipient : recipients) {
                try {
                    MimeMessage message = getMessage(recipient, content);
                    if (message == null) {
                        log.error("Failed to create message for {}", recipient.getEmail());
                        responses.add(new EmailSendResponse(500, "Failed to create message"));
                        continue;
                    }

                    log.debug("Sending message to {} ({})", recipient.getEmail(), recipient.getName());

                    // Send with detailed logging
                    sendWithLogging(message, recipient.getEmail());

                    log.info("Successfully sent email to {}", recipient.getEmail());
                    responses.add(new EmailSendResponse(200, "Email sent successfully"));

                } catch (org.springframework.mail.MailAuthenticationException e) {
                    log.error("SMTP authentication failed for {}: {}", recipient.getEmail(), e.getMessage());
                    responses.add(new EmailSendResponse(535, "SMTP authentication failed"));
                } catch (org.springframework.mail.MailSendException e) {
                    log.error("Failed to send email to {}: {}", recipient.getEmail(), e.getMessage());
                    if (e.getFailedMessages() != null) {
                        e.getFailedMessages()
                                .forEach((msg, ex) -> log.error("Failed message details: {}", ex.getMessage()));
                    }
                    responses.add(new EmailSendResponse(500, "Failed to send: " + e.getMessage()));
                } catch (Exception e) {
                    log.error("Unexpected error sending email to {}: {}", recipient.getEmail(), e.getMessage(), e);
                    responses.add(new EmailSendResponse(500, "Unexpected error: " + e.getMessage()));
                }
            }

            long successCount = responses.stream().filter(r -> r.getStatusCode() == 200).count();
            log.info("Batch send complete: {}/{} emails sent successfully", successCount, recipients.size());

            return responses;

        } catch (IllegalStateException e) {
            // Unsubscribe URL generation failed - don't send any emails in batch
            log.error("Cannot send batch emails due to unsubscribe URL generation failure: {}", e.getMessage());
            return recipients.stream()
                    .map(r -> new EmailSendResponse(500, "Failed to generate unsubscribe URL"))
                    .toList();
        } catch (Exception e) {
            log.error("Unexpected error in batch email send: {}", e.getMessage(), e);
            return List.of(new EmailSendResponse(500, "Batch processing failed: " + e.getMessage()));
        }
    }

    /**
     * Sends a MimeMessage with detailed transport event logging
     */
    private void sendWithLogging(MimeMessage message, String recipientEmail) {
        try {
            // Use the standard mailSender which handles connection pooling
            mailSender.send(message);

            // Alternative: Send with custom transport listener for detailed SMTP response
            // codes
            // sendWithTransportListener(message, recipientEmail);

        } catch (Exception e) {
            log.error("Error sending to {}: {}", recipientEmail, e.getMessage());
            throw e;
        }
    }

    // /**
    // * Alternative method to send with custom Transport for detailed SMTP response
    // * codes
    // * Note: This bypasses Spring's connection pooling, so use sparingly
    // */
    // @SuppressWarnings("unused")
    // private void sendWithTransportListener(MimeMessage message, String
    // recipientEmail) throws MessagingException {
    // Session session = message.getSession();
    // Transport transport = session.getTransport("smtp");

    // // Add transport listener for detailed event logging
    // transport.addTransportListener(new TransportListener() {
    // @Override
    // public void messageDelivered(TransportEvent e) {
    // log.info("✓ Message delivered to {}: {} valid addresses",
    // recipientEmail, e.getValidSentAddresses().length);
    // }

    // @Override
    // public void messageNotDelivered(TransportEvent e) {
    // log.error("✗ Message NOT delivered to {}: {} invalid addresses",
    // recipientEmail, e.getInvalidAddresses().length);
    // }

    // @Override
    // public void messagePartiallyDelivered(TransportEvent e) {
    // log.warn("⚠ Message partially delivered to {}: {} valid, {} invalid",
    // recipientEmail,
    // e.getValidSentAddresses().length,
    // e.getInvalidAddresses().length);
    // }
    // });

    // try {
    // transport.connect(
    // emailProperties.get(),
    // emailProperties.getSmtpPort(),
    // emailProperties.getSmtpUsername(),
    // emailProperties.getSmtpPassword());

    // log.debug("SMTP connection established for {}", recipientEmail);
    // transport.sendMessage(message, message.getAllRecipients());

    // } finally {
    // transport.close();
    // }
    // }

    /**
     * Constructs a MimeMessage for sending via SMTP.
     * 
     * @param recipient The email recipient
     * @param content   The email content
     * @return Constructed MimeMessage
     * @throws IllegalStateException if unsubscribe URL generation fails (indicates
     *                               system misconfiguration)
     */
    private MimeMessage getMessage(EmailRecipient recipient, EmailContent content) {
        String unsubscribeUrl;
        try {
            unsubscribeUrl = getUnsubscribeUrl(recipient.getEmail());
            if (unsubscribeUrl == null || unsubscribeUrl.isEmpty()) {
                throw new IllegalStateException("Unsubscribe URL generation returned null or empty");
            }
        } catch (Exception e) {
            log.error("Failed to generate unsubscribe URL for email: {}. Email will not be sent.", recipient.getEmail(),
                    e);
            throw new IllegalStateException("Cannot send email without valid unsubscribe URL (CAN-SPAM compliance)", e);
        }

        String htmlText = replacePlaceholders(content.getBody(), unsubscribeUrl);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper;
        try {
            helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(emailProperties.getSenderAddress());
            helper.setTo(recipient.getEmail());
            helper.setSubject(content.getSubject());
            helper.setText(htmlText, true); // true = HTML content

            log.debug("Created email message: from={}, to={}, subject={}",
                    emailProperties.getSenderAddress(),
                    recipient.getEmail(),
                    content.getSubject());

            return message;
        } catch (MessagingException e) {
            log.error("Failed to create email message for {}: {}", recipient.getEmail(), e.getMessage(), e);
            return null;
        }
    }

    private String replacePlaceholders(String htmlContents, String unsubscribeUrl) {
        return htmlContents.replaceAll("\\{\\{unsubscribe_url\\}\\}", unsubscribeUrl);
    }

    private String getUnsubscribeUrl(String email) {
        return unsubscribeTokenGenerator.generateUnsubscribeUrl(email);
    }
}
