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
        Mail mail = getMail(recipient, content);
        Personalization personalization = getPersonalization(recipient);
        mail.addPersonalization(personalization);

        try {
            Response response = sendGrid.api(generateRequest(mail));
            return new EmailSendResponse(response.getStatusCode(), response.getBody());
        } catch (IOException e) {
            log.error("Exception sending sendgrid email", e);
            return new EmailSendResponse(500, "Internal Server Error");
        }
    }

    @Override
    public List<EmailSendResponse> sendBatchedEmails(List<EmailRecipient> recipients, EmailContent content) {

        Mail mail = getMail(recipients.getFirst(), content);
        recipients.stream().forEach(r -> mail.addPersonalization(getPersonalization(r)));

        try {
            Response response = sendGrid.api(generateRequest(mail));
            return List.of(new EmailSendResponse(response.getStatusCode(), response.getBody()));
        } catch (IOException e) {
            log.error("Exception sending sendgrid email", e);
            return List.of(new EmailSendResponse(500, "Internal Server Error"));
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

    private Personalization getPersonalization(EmailRecipient recipient) {
        String unsubscribeUrl = getUnsubscribeUrl(recipient.getEmail());
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
