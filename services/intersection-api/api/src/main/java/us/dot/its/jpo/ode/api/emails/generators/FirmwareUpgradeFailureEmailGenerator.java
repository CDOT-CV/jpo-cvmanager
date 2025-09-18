package us.dot.its.jpo.ode.api.emails.generators;

import java.time.Instant;

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

        Context context = new Context();
        context.setVariable("head_title", "CV Manager - Firmware Upgrade Failure");
        context.setVariable("preview_text", "Firmware Upgrade Failure in CV Manager");
        context.setVariable("greeting", "Hello,");
        context.setVariable("content_1",
                String.format("%s: Failed to perform update on RSU %s due to the following error: %s\nStack Trace: %s",
                        data.getFailureType(), data.getRsuIp(), data.getMessage(), data.getStackTrace()));
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
                "CV-Manager Support Request: " + dateTimeFormatter.format(Instant.now()),
                htmlContent);
    }
}