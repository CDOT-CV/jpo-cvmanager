package us.dot.its.jpo.ode.api.emails.generators;

import java.time.Instant;
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

        Context context = new Context();
        context.setVariable("head_title", "CV Manager - Message Counts");
        context.setVariable("preview_text", "Message Counts from CV Manager");
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
                "CV-Manager Support Request: " + dateTimeFormatter.format(Instant.now()),
                htmlContent);
    }

    String getContent(MessageCountEmailContents data) {
        String content = String.format("<h2>%s %s Count Report %s UTC - %s UTC</h2>" +
                "<p>This is an automated email to report yesterday's ODE message counts for J2735 messages going in and out of the ODE. "
                +
                "In counts are the number of encoded messages received by the ODE from the load balancer. " +
                "Out counts are the number of decoded messages that have come out of the ODE in JSON form and " +
                "are available for querying in mongoDB. Ideally, these two counts should be identical. " +
                "Although, some deviation is expected due to count recording timings. Outbound counts exceeding " +
                "5% deviation with their corresponding inbound counts will be marked red. Outbound counts within the 5% deviation will be marked "
                +
                "green. Map and TIM Out counts are deduplicated so these are going to be lower at 1 per hour. The deviation is normalized with this in mind. "
                +
                "Any RSUs with a road name of \"Unknown\" are not recorded in the PostgreSQL database and might need to be added.</p>"
                +
                "<h3>RSU Message Counts</h3>",
                data.getOrganizationName(), data.getDeploymentTitle(), data.getStartDate().toString(),
                data.getEndDate().toString());
        String countsTable = generateCountTable(data);

        return String.format("%s\n%s", content, countsTable);
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