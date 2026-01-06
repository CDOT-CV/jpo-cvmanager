package us.dot.its.jpo.ode.api.emails.providers;

import java.util.List;

import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.EmailRecipient;
import us.dot.its.jpo.ode.api.models.emails.EmailSendResponse;

public interface EmailProvider {
    EmailSendResponse sendEmail(EmailRecipient recipient, EmailContent content);

    List<EmailSendResponse> sendBatchedEmails(List<EmailRecipient> recipients, EmailContent content);
}
