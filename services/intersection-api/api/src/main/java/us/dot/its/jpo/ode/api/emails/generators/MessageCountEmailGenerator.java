package us.dot.its.jpo.ode.api.emails.generators;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.contents.message_counts.MessageCountCountsItem;
import us.dot.its.jpo.ode.api.models.emails.contents.message_counts.MessageCountEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.message_counts.MessageCountRsuItem;

@Component
public class MessageCountEmailGenerator extends AbstractEmailGenerator<MessageCountEmailContents> {

    public MessageCountEmailGenerator(TemplateEngine templateEngine,
            UnsubscribeTokenGenerator unsubscribeTokenGenerator,
            EmailProperties emailProperties) {
        super(templateEngine, unsubscribeTokenGenerator, emailProperties);
    }

    @Override
    public EmailContent generateEmailBody(MessageCountEmailContents data) {

        Context context = this.generateEmailContextBasic();
        context.setVariable("preview_text", "Message Counts from CV Manager");
        context.setVariable("content_1", "<p>" + getContent(data) + "</p>");
        context.setVariable("footer_address", "CV-Manager Message Counts");

        String htmlContent = templateEngine.process("emails/announcement", context);

        return new EmailContent(
                "CV-Manager Message Counts",
                htmlContent);
    }

    String getContent(MessageCountEmailContents data) {
        String content = String.format(
                "<p>This is an automated email to report yesterday's ODE message counts for J2735 messages going in and out of the ODE. </p>"
                        +
                        "<p>Organization: %s<br>Deployment: %s<br>Start Date: %s UTC<br>End Date: %s UTC</p>"
                        +
                        "<p>`In counts` are the number of encoded messages received by the ODE from the load balancer. "
                        +
                        "`Out counts` are the number of decoded messages that have come out of the ODE in JSON form and "
                        +
                        "are available for querying in mongoDB. Ideally these two counts should be identical, "
                        +
                        "although some deviation is expected due to count recording timings.<br>"
                        +
                        "Map and TIM Out counts are deduplicated so these are going to be lower at 1 per hour. The deviation is normalized with this in mind."
                        +
                        "<h3>RSU Message Counts</h3>"
                        +
                        "<div style=\"margin: 16px 0; padding: 12px; background-color: #f5f5f5; border-radius: 4px; display: inline-block;\">"
                        +
                        "<strong>Out Counts:</strong>&nbsp;&nbsp;"
                        +
                        "<span style=\"background-color: #a4ffa1; padding: 4px 12px; margin: 0 4px; border-radius: 3px;\">Green: ≤5%% deviation</span>"
                        +
                        "<span style=\"background-color: #ff7373; padding: 4px 12px; margin: 0 4px; border-radius: 3px;\">Red: >5%% deviation</span>"
                        +
                        "</div>",
                data.getOrganizationName(), data.getDeploymentTitle(), data.getStartDate().toString(),
                data.getEndDate().toString());
        String countsTable = generateCountTable(data);

        return String.format("%s %s", content, countsTable);
    }

    public static String generateTableHeader(List<String> messageTypeList) {
        StringBuilder html = new StringBuilder();
        html.append("<thead>\n")
                .append("<tr style=\"text-align: center;background-color: #b0dfff;\">\n")
                .append("<th style=\"padding: 12px;\">RSU</th>\n")
                .append("<th style=\"padding: 12px;\">Road</th>\n");

        for (String type : messageTypeList) {
            html.append("<th style=\"padding: 12px;\">").append(type).append(" In</th>\n");
            html.append("<th style=\"padding: 12px;\">").append(type).append(" Out</th>\n");
        }

        html.append("</tr>\n</thead>\n");
        return html.toString();
    }

    public static String generateTableRow(
            String rsuIp,
            MessageCountRsuItem rsuCountsItem,
            String rowStyle,
            List<String> messageTypeList) {
        StringBuilder html = new StringBuilder();
        html.append("<tr style=\"").append(rowStyle).append("\">\n")
                .append("<td>").append(rsuIp).append("</td>\n")
                .append("<td>").append(rsuCountsItem.getPrimaryRoute()).append("</td>\n");

        Map<String, MessageCountCountsItem> counts = rsuCountsItem.getMessageCountsByType();
        for (String type : messageTypeList) {
            MessageCountCountsItem countsItem = counts.get(type);
            html.append("<td>").append(countsItem.getIn()).append("</td>\n");
            html.append("<td style=\"background-color: ")
                    .append(diffToColor(countsItem.getDiffPercent()))
                    .append(";\">")
                    .append(countsItem.getOut())
                    .append("</td>\n");
        }

        html.append("</tr>\n");
        return html.toString();
    }

    public static String generateCountTable(
            MessageCountEmailContents countsData) {
        if (countsData == null || countsData.getRsuCounts().isEmpty()) {
            System.err.println("RSU dictionary is empty. Most likely an issue with PostgreSQL");
            return "";
        }

        StringBuilder html = new StringBuilder();
        html.append("<table class=\"dataframe\">\n")
                .append(generateTableHeader(countsData.getMessageTypeList()))
                .append("<tbody>\n");

        boolean styleSwitch = false;
        for (MessageCountRsuItem rsuItem : countsData.getRsuCounts()) {
            String rsuIp = rsuItem.getRsuIp();
            Map<String, MessageCountCountsItem> countEntry = rsuItem.getMessageCountsByType();
            String rowStyle = styleSwitch
                    ? "text-align: center;background-color: #f2f2f2;"
                    : "text-align: center;";
            styleSwitch = !styleSwitch;

            // Calculate differences between In and Out counts (%)
            for (String type : countEntry.keySet()) {
                MessageCountCountsItem countsItem = countEntry.get(type);
                int inCount = countsItem.getIn();
                int outCount = countsItem.getOut();

                double diffPercent;
                if (type.equalsIgnoreCase("bsm") || type.equalsIgnoreCase("tim")) {
                    diffPercent = (inCount != 0 && outCount == 0) || (outCount > inCount) ? 6 : 0;
                } else {
                    int x = type.equalsIgnoreCase("map") ? 3600 : 1;
                    if (inCount != 0) {
                        diffPercent = Math.abs(outCount / Math.ceil((double) inCount / x) - 1) * 100;
                    } else {
                        diffPercent = outCount > inCount ? 6 : 0;
                    }
                }
                countsItem.setDiffPercent(diffPercent);
            }

            html.append(generateTableRow(rsuIp, rsuItem, rowStyle, countsData.getMessageTypeList()));
        }

        html.append("</tbody>\n</table>");
        return html.toString();
    }

    private static String diffToColor(Number val) {
        return val.doubleValue() > 5 ? "#ff7373" : "#a4ffa1";
    }
}