package us.dot.its.jpo.ode.api.emails.generators;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.contents.RsuErrorSummaryEmailContents;

@Component
public class RsuErrorSummaryEmailGenerator extends AbstractEmailGenerator<RsuErrorSummaryEmailContents> {

    public RsuErrorSummaryEmailGenerator(TemplateEngine templateEngine,
            UnsubscribeTokenGenerator unsubscribeTokenGenerator,
            EmailProperties emailProperties) {
        super(templateEngine, unsubscribeTokenGenerator, emailProperties);
    }

    @Override
    public EmailContent generateEmailBody(RsuErrorSummaryEmailContents data) {

        Context context = this.generateEmailContextBasic();
        context.setVariable("preview_text", "RSU Error Summary from CV Manager");
        context.setVariable("content_1", data.getMessage());
        context.setVariable("footer_address", "RSU Error Summary");
        context.setVariable("unsubscribe_pre_text", "This email was sent to you on request of a CV-Manager user. ");
        context.setVariable("unsubscribe_link_text", "");
        context.setVariable("unsubscribe_href", "");

        String htmlContent = templateEngine.process("emails/email_template", context);

        return new EmailContent(
                data.getSubject(),
                htmlContent);
    }
}