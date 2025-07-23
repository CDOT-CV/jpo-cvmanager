package us.dot.its.jpo.ode.api.services;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.postmarkapp.postmark.client.ApiClient;
import com.postmarkapp.postmark.client.data.model.message.Message;
import com.postmarkapp.postmark.client.exception.PostmarkException;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.models.emails.EmailCategory;
import us.dot.its.jpo.ode.api.models.emails.EmailFrequency;
import us.dot.its.jpo.ode.api.models.emails.EmailWrapper;

@Slf4j
@Service
public class EmailService {

    private final SendGrid sendGrid;
    private final ApiClient postmark;
    private final EmailProperties emailProperties;
    private final PostgresService postgresService;

    @Autowired
    public EmailService(SendGrid sendGrid, ApiClient postmark,
            EmailProperties emailProperties, PostgresService postgresService) {
        this.sendGrid = sendGrid;
        this.postmark = postmark;
        this.emailProperties = emailProperties;
        this.postgresService = postgresService;
    }

    public void sendEmailViaSendGrid(String to, String subject, String text, String unsubscribeUrl) {
        Email fromEmail = new Email(emailProperties.getSenderAddress());
        Email toEmail = new Email(to);
        Content content = new Content("text/html", text);
        Mail mail = new Mail(fromEmail, subject, toEmail, content);

        // Add the List-Unsubscribe header
        mail.personalization.get(0).addHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");

        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = this.sendGrid.api(request);
            if (response.getStatusCode() >= 400) {
                log.error("Failed to send email via SendGrid with code {}: {}", response.getStatusCode(),
                        response.getBody());
            } else {
                log.info("Email sent successfully via SendGrid to {}", to);
            }
        } catch (IOException e) {
            log.error("Exception sending sendgrid email", e);
        }
    }

    public void sendEmailViaPostmark(String to, String subject, String text, String unsubscribeUrl) {

        String htmlText = text.replaceAll("\n", "<br>");

        Message message = new Message(
                emailProperties.getSenderAddress(),
                to,
                subject,
                htmlText);

        // Add the List-Unsubscribe header
        message.addHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");

        try {
            postmark.deliverMessage(message);
        } catch (PostmarkException | IOException e) {
            log.error("Exception sending postmark email", e);
        }
    }

    public void sendEmailViaSpringMail(String to, String subject, String text, String unsubscribeUrl) {
        Email fromEmail = new Email("your-email@example.com"); // Replace with your sender email
        Email toEmail = new Email(to);
        Content content = new Content("text/plain", text);
        Mail mail = new Mail(fromEmail, subject, toEmail, content);

        // Add the List-Unsubscribe header
        mail.personalization.get(0).addHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");

        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            sendGrid.api(request);
        } catch (IOException e) {
            log.error("Exception sending SendGrid email", e);
        }
    }

    public void sendSimpleMessage(String to, String subject, String text, String unsubscribeUrl) {
        switch (emailProperties.getBroker()) {
            case POSTMARK:
                sendEmailViaPostmark(to, subject, text, unsubscribeUrl);
                break;
            case SENDGRID:
                sendEmailViaSendGrid(to, subject, text, unsubscribeUrl);
                break;
            case SMTP:
            default:
                sendEmailViaSpringMail(to, subject, text, unsubscribeUrl);
                break;
        }
    }

    public void sendEmails(List<EmailWrapper> wrappers) {
        wrappers.stream()
                .forEach(wrapper -> sendEmail(wrapper));
    }

    public void sendEmail(EmailWrapper wrapper) {
        sendSimpleMessage(wrapper.getRecipientEmail(), wrapper.getSubject(), wrapper.getBody(),
                wrapper.getUnsubscribeUrl());
    }

    public List<String> getUsersForNotificationType(EmailCategory category, EmailFrequency frequency) {
        // TODO: Filter by email frequency
        return postgresService.getUsersByNotificationType(category.getCategoryKey());
    }
}