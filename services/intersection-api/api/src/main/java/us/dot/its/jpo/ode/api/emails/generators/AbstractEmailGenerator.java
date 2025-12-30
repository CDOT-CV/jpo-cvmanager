package us.dot.its.jpo.ode.api.emails.generators;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;

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

    public EmailContent generateEmailContent(T data) {
        return generateEmailBody(data);
    }

    public Context generateEmailContextBasic() {

        Context context = new Context();
        context.setVariable("greeting", "Hello CV-Manager User,");
        context.setVariable("action_button_text", "Navigate to the CV-Manager");
        context.setVariable("action_button_href",
                String.format("%s", emailProperties.getCvmgrFrontEndUri()));
        context.setVariable("content_2",
                "If not actionable, please forward this request on to the relevant party.");
        context.setVariable("signature",
                "This was an automated email from the CV Manager. Please do not reply to this email.");
        context.setVariable("unsubscribe_pre_text", "If you no longer wish to receive these emails, please ");
        context.setVariable("unsubscribe_link_text", "Unsubscribe");
        context.setVariable("unsubscribe_href", "{{unsubscribe_url}}");
        context.setVariable("backgroundColor", "#f4f5f6");
        context.setVariable("contentBackgroundColor", "#f4f5f6");
        context.setVariable("tableMainBackgroundColor", "#ffffff");
        context.setVariable("tableMainBorderColor", "#eaebed");
        context.setVariable("tableHoverColor", "#ec8208ff");
        context.setVariable("tableButtonColor", "#0867ec");
        context.setVariable("btnColor", "#0867ec");
        context.setVariable("btnFontColor", "#ffffff");
        context.setVariable("btnHoverColor", "#ec8208ff");
        context.setVariable("footerFontColor", "#9a9ea6");

        return context;
    }

    /**
     * Template method to generate the email body.
     * Subclasses must implement the specific logic for their email type.
     *
     * @param emailAddress The recipient's email address.
     * @param data         The data required to generate the email body.
     * @return The generated email body as a String.
     */
    public abstract EmailContent generateEmailBody(T data);
}