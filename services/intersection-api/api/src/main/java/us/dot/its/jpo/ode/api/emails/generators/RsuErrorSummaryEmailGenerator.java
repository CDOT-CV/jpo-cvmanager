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

        Context context = new Context();
        context.setVariable("head_title", "CV Manager - RSU Error Summary");
        context.setVariable("preview_text", "RSU Error Summary from CV Manager");
        context.setVariable("greeting", "Hello,");
        context.setVariable("content_1", getContent(data));
        context.setVariable("action_button_text", "Navigate to the CV-Manager");
        context.setVariable("action_button_href",
                String.format("%s", emailProperties.getCvmgrFrontEndUri()));
        context.setVariable("content_2",
                "If not actionable, please forward this request on to the relevant party.");
        context.setVariable("signature",
                "This was an automated email from the CV Manager. Please do not reply to this email.");
        context.setVariable("footer_address", "CV-Manager Firmware Upgrade Failure");
        context.setVariable("unsubscribe_pre_text", "If you no longer wish to receive these emails, please ");
        context.setVariable("unsubscribe_link_text", "Unsubscribe");
        context.setVariable("unsubscribe_href", "{{unsubscribe_url}}");

        String htmlContent = templateEngine.process("emails/announcement", context);

        return new EmailContent(
                "CV-Manager RSU Error Summary for " + data.getRsuIp(),
                htmlContent);
    }

    private String getContent(RsuErrorSummaryEmailContents data) {
        String content = String.format("<h2>RSU Error Summary Email</h2>" +
                "<br />" +
                "<p>Hello,</p>" +
                "<p>Below is the status summary for RSU %s at %s UTC:</p>" +
                "<table>" +
                "<tr>" +
                "<th>Online Status</th>" +
                "<th>SCMS Status</th>" +
                "<th>Certificate Status</th>" +
                "</tr>" +
                "<tr>" +
                "<td>RSU %s</td>" +
                "<td>%s</td>" +
                "</tr>" +
                "</table>",
                data.getRsuIp(),
                dateTimeFormatter.format(data.getTimestamp()),
                data.getOnlineStatus(),
                data.getScmsStatus(),
                data.getCertificateStatus());
        return content;
    }
}