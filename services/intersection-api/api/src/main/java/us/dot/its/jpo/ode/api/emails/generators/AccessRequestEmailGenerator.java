package us.dot.its.jpo.ode.api.emails.generators;

import java.util.List;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.contents.access_requests.AccessRequestEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.access_requests.OrganizationAccess;

@Component
public class AccessRequestEmailGenerator extends AbstractEmailGenerator<AccessRequestEmailContents> {

    public AccessRequestEmailGenerator(TemplateEngine templateEngine,
            UnsubscribeTokenGenerator unsubscribeTokenGenerator,
            EmailProperties emailProperties) {
        super(templateEngine, unsubscribeTokenGenerator, emailProperties);
    }

    @Override
    public EmailContent generateEmailBody(AccessRequestEmailContents data) {

        Context context = this.generateEmailContextBasic();
        context.setVariable("preview_text", "Requesting Additional Access in CV Manager");
        context.setVariable("content_1", getContent(data));
        context.setVariable("footer_address", "CV-Manager Access Request");

        String htmlContent = templateEngine.process("emails/announcement", context);

        return new EmailContent(
                "CV-Manager Access Request",
                htmlContent);
    }

    private String getContent(AccessRequestEmailContents data) {
        return String.format(
                "<p>The user %s has requested access to the CV Manager for the following organizations:<br></p>%s",
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