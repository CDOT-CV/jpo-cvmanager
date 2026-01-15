package us.dot.its.jpo.ode.api.emails.providers;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.EmailRecipient;
import us.dot.its.jpo.ode.api.models.emails.EmailSendResponse;

@Slf4j
@Component
@Qualifier("SENDGRID")
@RequiredArgsConstructor
public class EmailProviderSendGrid implements EmailProvider {

    private final EmailProperties emailProperties;
    private final UnsubscribeTokenGenerator unsubscribeTokenGenerator;
    private final SendGrid sendGrid;

    @Override
    public EmailSendResponse sendEmail(EmailRecipient recipient, EmailContent content) {
        try {
            Mail mail = getMail(recipient, content);
            Personalization personalization = getPersonalization(recipient);
            mail.addPersonalization(personalization);

            Response response = sendGrid.api(generateRequest(mail));
            return new EmailSendResponse(response.getStatusCode(), response.getBody());
        } catch (IllegalStateException e) {
            // Unsubscribe URL generation failed - don't send email
            log.error("Cannot send email due to unsubscribe URL generation failure: {}", e.getMessage());
            return new EmailSendResponse(500, "Failed to generate unsubscribe URL");
        } catch (IOException e) {
            log.error("Exception sending sendgrid email", e);
            return new EmailSendResponse(500, "Internal Server Error");
        }
    }

    @Override
    public List<EmailSendResponse> sendBatchedEmails(List<EmailRecipient> recipients, EmailContent content) {
        try {
            Mail mail = getMail(recipients.getFirst(), content);
            recipients.stream().forEach(r -> mail.addPersonalization(getPersonalization(r)));

            Response response = sendGrid.api(generateRequest(mail));
            return List.of(new EmailSendResponse(response.getStatusCode(), response.getBody()));
        } catch (IllegalStateException e) {
            // Unsubscribe URL generation failed - don't send any emails in batch
            log.error("Cannot send batch emails due to unsubscribe URL generation failure: {}", e.getMessage());
            return recipients.stream()
                    .map(r -> new EmailSendResponse(500, "Failed to generate unsubscribe URL"))
                    .toList();
        } catch (IOException e) {
            log.error("Exception sending sendgrid email batch", e);
            return recipients.stream()
                    .map(r -> new EmailSendResponse(500, "Internal Server Error"))
                    .toList();
        }
    }

    private Mail getMail(EmailRecipient recipient, EmailContent content) {
        Email fromEmail = new Email(emailProperties.getSenderAddress());
        Content sendGridContent = new Content("text/html", content.getBody());

        Mail mail = new Mail(
                fromEmail,
                content.getSubject(),
                recipient.toSendGridEmail(),
                sendGridContent);
        return mail;
    }

    /**
     * Creates a Personalization object with unsubscribe URL for SendGrid.
     * 
     * @param recipient The email recipient
     * @return Personalization object with recipient and unsubscribe URL
     * @throws IllegalStateException if unsubscribe URL generation fails (indicates
     *                               system misconfiguration)
     */
    private Personalization getPersonalization(EmailRecipient recipient) {
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

        Personalization personalization = new Personalization();
        personalization.addTo(recipient.toSendGridEmail());
        personalization.addDynamicTemplateData("unsubscribe_url", unsubscribeUrl);
        personalization.addHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");
        return personalization;
    }

    private Request generateRequest(Mail mail) throws IOException {
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());
        return request;
    }

    private String getUnsubscribeUrl(String email) {
        return unsubscribeTokenGenerator.generateUnsubscribeUrl(email);
    }
}
