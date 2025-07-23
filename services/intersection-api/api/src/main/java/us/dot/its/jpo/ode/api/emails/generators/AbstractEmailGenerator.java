package us.dot.its.jpo.ode.api.emails.generators;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailWrapper;

@Component
public abstract class AbstractEmailGenerator<T> {

    protected final TemplateEngine templateEngine;
    protected final UnsubscribeTokenGenerator unsubscribeTokenGenerator;
    protected final EmailProperties emailProperties;
    protected final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("UTC"));

    public AbstractEmailGenerator(TemplateEngine templateEngine, UnsubscribeTokenGenerator unsubscribeTokenGenerator,
            EmailProperties emailProperties) {
        this.templateEngine = templateEngine;
        this.unsubscribeTokenGenerator = unsubscribeTokenGenerator;
        this.emailProperties = emailProperties;
    }

    public List<EmailWrapper> generateEmailBodies(List<String> recipients,
            T data) {
        return recipients.stream().map((emailAddress) -> generateEmailBody(emailAddress, data))
                .filter(wrapper -> wrapper != null).toList();

    }

    /**
     * Template method to generate the email body.
     * Subclasses must implement the specific logic for their email type.
     *
     * @param emailAddress The recipient's email address.
     * @param data         The data required to generate the email body.
     * @return The generated email body as a String.
     */
    public abstract EmailWrapper generateEmailBody(String emailAddress, T data);
}