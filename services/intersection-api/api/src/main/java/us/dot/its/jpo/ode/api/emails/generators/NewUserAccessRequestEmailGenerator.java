package us.dot.its.jpo.ode.api.emails.generators;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.contents.new_user_access_requests.NewUserAccessRequestEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.new_user_access_requests.OrganizationAccess;

@Component
public class NewUserAccessRequestEmailGenerator extends AbstractEmailGenerator<NewUserAccessRequestEmailContents> {

    public NewUserAccessRequestEmailGenerator(TemplateEngine templateEngine,
            UnsubscribeTokenGenerator unsubscribeTokenGenerator,
            EmailProperties emailProperties) {
        super(templateEngine, unsubscribeTokenGenerator, emailProperties);
    }

    @Override
    public EmailContent generateEmailBody(NewUserAccessRequestEmailContents data) {

        Context context = new Context();
        context.setVariable("head_title", "CV Manager - New User Access Request");
        context.setVariable("preview_text", "New User Requesting Access in CV Manager");
        context.setVariable("greeting", "Hello,");
        context.setVariable("content_1", getContent(data));
        context.setVariable("action_button_text", "Navigate to the CV-Manager");
        context.setVariable("action_button_href",
                String.format("%s", emailProperties.getCvmgrFrontEndUri()));
        context.setVariable("content_2",
                "If not actionable, please forward this request on to the relevant party.");
        context.setVariable("signature",
                "This was an automated email from the CV Manager. Please do not reply to this email.");
        context.setVariable("footer_address", "CV-Manager New User Access Request");
        context.setVariable("unsubscribe_pre_text", "If you no longer wish to receive these emails, please ");
        context.setVariable("unsubscribe_link_text", "Unsubscribe");
        context.setVariable("unsubscribe_href", "{{unsubscribe_url}}");

        String htmlContent = templateEngine.process("emails/announcement", context);

        return new EmailContent(
                "CV-Manager Support Request: " + dateTimeFormatter.format(Instant.now()),
                htmlContent);
    }

    private String getContent(NewUserAccessRequestEmailContents data) {
        return String.format(
                "New user %s has requested access to the CV Manager. They have requested the following permissions:\n\n%s",
                data.getEmail(), getOrgRoleTable(data.getAccessRequests()));
    }

    private String getOrgRoleTable(List<OrganizationAccess> accessRequests) {
        StringBuilder table = new StringBuilder();
        table.append("<table style=\"width:100%; border: 1px solid black; border-collapse: collapse;\">");
        table.append("<tr>");
        table.append("<th style=\"border: 1px solid black; padding: 8px; text-align: left;\">Organization</th>");
        table.append("<th style=\"border: 1px solid black; padding: 8px; text-align: left;\">Requested Role</th>");
        table.append("</tr>");

        for (OrganizationAccess access : accessRequests) {
            table.append("<tr>");
            table.append(String.format("<td style=\"border: 1px solid black; padding: 8px; text-align: left;\">%s</td>",
                    access.getOrganizationName()));
            table.append(String.format("<td style=\"border: 1px solid black; padding: 8px; text-align: left;\">%s</td>",
                    access.getRole()));
            table.append("</tr>");
        }

        table.append("</table>");
        return table.toString();
    }
}