package us.dot.its.jpo.ode.api.emails.generators;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.contents.FirmwareUpgradeFailureEmailContents;

@Component
public class FirmwareUpgradeFailureEmailGenerator extends AbstractEmailGenerator<FirmwareUpgradeFailureEmailContents> {

    public FirmwareUpgradeFailureEmailGenerator(TemplateEngine templateEngine,
            UnsubscribeTokenGenerator unsubscribeTokenGenerator,
            EmailProperties emailProperties) {
        super(templateEngine, unsubscribeTokenGenerator, emailProperties);
    }

    @Override
    public EmailContent generateEmailBody(FirmwareUpgradeFailureEmailContents data) {

        Context context = this.generateEmailContextBasic();
        context.setVariable("preview_text", "Firmware Upgrade Failure in CV Manager");

        // Improved formatting: error message as a title and message below
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("<h2 style=\"color:#d32f2f;\">Firmware Upgrade Failure Detected</h2>");
        contentBuilder.append("<p>A firmware upgrade command failed on RSU <strong>")
                .append(escapeHtml(data.getRsuIp()))
                .append("</strong>.</p>");
        contentBuilder.append("<table style=\"border-collapse:collapse;margin-bottom:16px;\">")
                .append("<tr><td style=\"font-weight:bold;padding:4px 8px;\">Update type:</td>")
                .append("<td style=\"padding:4px 8px;\">")
                .append(escapeHtml(data.getFailureType()))
                .append("</td></tr>")
                .append("</table>");
        // Error message as a section
        contentBuilder.append("<div style=\"margin-bottom:16px;\">")
                .append("<div style=\"font-weight:bold; margin-bottom:4px;\">Error Message</div>")
                .append("<div style=\"padding:8px 12px; background:#fff3e0; border-left:4px solid #d32f2f; border-radius:3px; margin-bottom:8px;\">")
                .append(escapeHtml(data.getMessage()))
                .append("</div>")
                .append("</div>");
        contentBuilder
                .append("<div style=\"margin-bottom:16px;\"><span style=\"font-weight:bold;\">Stack Trace:</span><br>")
                .append("<pre style=\"background:#f8f8f8;border:1px solid #eee;padding:12px;font-size:13px;line-height:1.4;overflow-x:auto;\">")
                .append(escapeHtml(data.getStackTrace()))
                .append("</pre></div>");

        context.setVariable("content_1", contentBuilder.toString());
        context.setVariable("footer_address", "CV-Manager Firmware Upgrade Failure");

        String htmlContent = templateEngine.process("emails/email_template", context);

        return new EmailContent(
                "CV-Manager Firmware Upgrade Failure",
                htmlContent);
    }
}