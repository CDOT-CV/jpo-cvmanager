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
        context.setVariable("content_1",
                String.format(
                        "<p>A firmware upgrade command failed on RSU %s.<br><br><strong>Update type:</strong> %s<br><strong>Error message:</strong> %s<br><strong>Stack Trace:</strong> %s</p>",
                        data.getRsuIp(), data.getFailureType(), data.getMessage(), data.getStackTrace()));
        context.setVariable("footer_address", "CV-Manager Firmware Upgrade Failure");

        String htmlContent = templateEngine.process("emails/announcement", context);

        return new EmailContent(
                "CV-Manager Support Request",
                htmlContent);
    }
}