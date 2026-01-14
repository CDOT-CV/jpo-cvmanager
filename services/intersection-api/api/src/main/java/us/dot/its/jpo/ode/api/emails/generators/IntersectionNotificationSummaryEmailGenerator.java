package us.dot.its.jpo.ode.api.emails.generators;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import us.dot.its.jpo.conflictmonitor.monitor.models.notifications.Notification;
import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.contents.IntersectionNotificationSummaryEmailContents;

@Component
public class IntersectionNotificationSummaryEmailGenerator
        extends AbstractEmailGenerator<IntersectionNotificationSummaryEmailContents> {

    private final String EMAIL_TEMPLATE = "emails/announcement";

    public IntersectionNotificationSummaryEmailGenerator(TemplateEngine templateEngine,
            UnsubscribeTokenGenerator unsubscribeTokenGenerator,
            EmailProperties emailProperties) {
        super(templateEngine, unsubscribeTokenGenerator, emailProperties);
    }

    @Override
    public EmailContent generateEmailBody(IntersectionNotificationSummaryEmailContents data) {

        Context context = this.generateEmailContextBasic();
        context.setVariable("preview_text", "New Notifications in CV Manager");
        context.setVariable("content_1", "<p>" + getEmailText(data.getNotifications()) + "</p>");
        context.setVariable("footer_address", "CV-Manager Automated Notifications");

        String htmlContent = templateEngine.process(EMAIL_TEMPLATE, context);

        return new EmailContent(
                "New CV-Manager Intersection Notifications",
                htmlContent);
    }

    public String getEmailText(List<Notification> notifications) {

        String messageBody = "There are new Notifications to review in the conflict monitor application. Please review the Notifications below, or log into the CV-Manager to Analyze these notifications<br>";

        for (Notification notification : notifications) {
            messageBody += "<br><strong>Heading:</strong> " + notification.getNotificationHeading() + "<br>";
            messageBody += "<strong>Description:</strong> " + notification.getNotificationText() + "<br>";
            messageBody += "<strong>Intersection ID:</strong> " + notification.getIntersectionID() + "<br>";
            messageBody += "<strong>Generated At:</strong> "
                    + dateTimeFormatter.format(
                            Instant.ofEpochMilli(notification.getNotificationGeneratedAt()))
                    + "<br>";
        }

        return messageBody;
    }
}