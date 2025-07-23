package us.dot.its.jpo.ode.api.emails.generators;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import us.dot.its.jpo.conflictmonitor.monitor.models.notifications.Notification;
import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailWrapper;
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
        public EmailWrapper generateEmailBody(String emailAddress, IntersectionNotificationSummaryEmailContents data) {
                String unsubscribeUrl = unsubscribeTokenGenerator.generateUnsubscribeUrl(emailAddress);

                Context context = new Context();
                context.setVariable("head_title", "CV Manager - New Notifications");
                context.setVariable("preview_text", "New Notifications in CV Manager");
                context.setVariable("greeting", "Hello,");
                context.setVariable("content_1", getEmailText(data.getNotifications()));
                context.setVariable("action_button_text", "View Notifications in the CV-Manager");
                context.setVariable("action_button_href",
                                String.format("%s/intersectionDashboard/notifications",
                                                emailProperties.getCvmgrFrontEndUri()));
                context.setVariable("content_2",
                                "Any notifications marked with CBR are Critical and should be reviewed immediately.");
                context.setVariable("signature",
                                "This was an automated email from the CV Manager. Please do not reply to this email.");
                context.setVariable("footer_address", "CV-Manager Automated Notifications");
                context.setVariable("unsubscribe_pre_text", "If you no longer wish to receive these emails, please ");
                context.setVariable("unsubscribe_link_text", "Unsubscribe");
                context.setVariable("unsubscribe_href", unsubscribeUrl);

                String htmlContent = templateEngine.process(EMAIL_TEMPLATE, context);

                return new EmailWrapper(
                                emailAddress,
                                "CV-Manager New CM Notifications: " + dateTimeFormatter.format(Instant.now()),
                                htmlContent,
                                unsubscribeUrl);
        }

        public String getEmailText(List<Notification> notifications) {

                String messageBody = "There are new Notifications to review in the conflict monitor application. Please review the Notifications below, or log into the Conflict Visualizer to Analyze these notifications";

                for (Notification notification : notifications) {
                        messageBody += "\n\nNotification : " + notification.getNotificationHeading() + "\n";
                        messageBody += "\t" + notification.getNotificationText() + "\n";
                        messageBody += "\tIntersection ID: " + notification.getIntersectionID() + "\n";
                        messageBody += "\tGenerated At: "
                                        + dateTimeFormatter.format(
                                                        Instant.ofEpochMilli(notification.getNotificationGeneratedAt()))
                                        + "\n";
                }

                return messageBody;
        }
}