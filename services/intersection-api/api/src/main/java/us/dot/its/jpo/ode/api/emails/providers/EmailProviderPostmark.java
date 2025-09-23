package us.dot.its.jpo.ode.api.emails.providers;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.postmarkapp.postmark.client.ApiClient;
import com.postmarkapp.postmark.client.data.model.message.Message;
import com.postmarkapp.postmark.client.data.model.message.MessageResponse;
import com.postmarkapp.postmark.client.exception.PostmarkException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.EmailRecipient;
import us.dot.its.jpo.ode.api.models.emails.EmailSendResponse;

@Slf4j
@Component
@Qualifier("POSTMARK")
@RequiredArgsConstructor
public class EmailProviderPostmark implements EmailProvider {

    private final EmailProperties emailProperties;
    private final UnsubscribeTokenGenerator unsubscribeTokenGenerator;
    private final ApiClient postmark;

    @Override
    public EmailSendResponse sendEmail(EmailRecipient recipient, EmailContent content) {
        Message message = getMessage(recipient, content);

        try {
            MessageResponse response = postmark.deliverMessage(message);
            return new EmailSendResponse(response.getErrorCode(), response.getMessage());
        } catch (PostmarkException | IOException e) {
            log.error("Exception sending postmark email", e);
            return new EmailSendResponse(500, "Internal Server Error");
        }
    }

    @Override
    public List<EmailSendResponse> sendBatchedEmails(List<EmailRecipient> recipients, EmailContent content) {

        List<Message> messages = recipients.stream().map(r -> getMessage(r, content)).toList();

        try {
            List<MessageResponse> responses = postmark.deliverMessage(messages);
            return responses.stream().map(r -> new EmailSendResponse(r.getErrorCode(), r.getMessage())).toList();
        } catch (PostmarkException | IOException e) {
            log.error("Exception sending postmark email", e);
            return List.of(new EmailSendResponse(500, "Internal Server Error"));
        }
    }

    private Message getMessage(EmailRecipient recipient, EmailContent content) {
        String unsubscribeUrl = getUnsubscribeUrl(recipient.getEmail());
        String htmlText = replacePlaceholders(content.getBody(), unsubscribeUrl);

        Message message = new Message(
                emailProperties.getSenderAddress(),
                recipient.getEmail(),
                content.getSubject(),
                htmlText);

        // Add the List-Unsubscribe header
        message.addHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");

        return message;
    }

    private String replacePlaceholders(String htmlContents, String unsubscribeUrl) {
        return htmlContents.replaceAll("\\{\\{unsubscribe_url\\}\\}", unsubscribeUrl).replaceAll("\n", "<br>");
    }

    private String getUnsubscribeUrl(String email) {
        return unsubscribeTokenGenerator.generateUnsubscribeUrl(email);
    }
}
