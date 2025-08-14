package us.dot.its.jpo.ode.api.emails.generators;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.MessageCount;
import us.dot.its.jpo.ode.api.models.emails.EmailWrapper;
import us.dot.its.jpo.ode.api.models.emails.contents.message_counts.DailyCountEmailContents;

@Slf4j
@Component
public class DailyCountEmailGenerator extends AbstractEmailGenerator<DailyCountEmailContents> {

    private static final String[] MESSAGE_TYPES = { "BSM", "TIM", "Map", "SPaT", "SRM", "SSM" };
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public DailyCountEmailGenerator(TemplateEngine templateEngine, UnsubscribeTokenGenerator unsubscribeTokenGenerator,
            EmailProperties emailProperties) {
        super(templateEngine, unsubscribeTokenGenerator, emailProperties);
    }

    @Override
    public EmailWrapper generateEmailBody(String emailAddress, DailyCountEmailContents data) {
        try {
            String subject = String.format("%s %s Counts", data.getOrganizationName(), data.getDeploymentTitle());
            String body = generateEmailHtml(data);
            String unsubscribeUrl = unsubscribeTokenGenerator.generateUnsubscribeUrl(emailAddress);

            return new EmailWrapper(emailAddress, subject, body, unsubscribeUrl);
        } catch (Exception e) {
            log.error("Error generating daily count email for {}: {}", emailAddress, e.getMessage());
            return null;
        }
    }

    private String generateEmailHtml(DailyCountEmailContents data) {
        StringBuilder html = new StringBuilder();

        // Header
        String startDate = data.getStartDateTime().format(DATE_FORMATTER);
        String endDate = data.getEndDateTime().format(DATE_FORMATTER);

        html.append(String.format("<h2>%s %s Count Report %s UTC - %s UTC</h2>",
                data.getOrganizationName(), data.getDeploymentTitle(), startDate, endDate));

        // Description
        html.append(
                "<p>This is an automated email to report yesterday's ODE message counts for J2735 messages going in and out of the ODE. ");
        html.append("In counts are the number of encoded messages received by the ODE from the load balancer. ");
        html.append("Out counts are the number of decoded messages that have come out of the ODE in JSON form and ");
        html.append("are available for querying in mongoDB. Ideally, these two counts should be identical. ");
        html.append("Although, some deviation is expected due to count recording timings. Outbound counts exceeding ");
        html.append(
                "5% deviation with their corresponding inbound counts will be marked red. Outbound counts within the 5% deviation will be marked ");
        html.append(
                "green. Map and TIM Out counts are deduplicated so these are going to be lower at 1 per hour. The deviation is normalized with this in mind. ");
        html.append(
                "Any RSUs with a road name of \"Unknown\" are not recorded in the PostgreSQL database and might need to be added.</p>");

        html.append("<h3>RSU Message Counts</h3>");

        // Generate table for each organization
        for (Map.Entry<String, List<MessageCount>> entry : data.getRsuCountsByOrganization().entrySet()) {
            String orgName = entry.getKey();
            List<MessageCount> counts = entry.getValue();

            if (!counts.isEmpty()) {
                html.append(generateCountTable(orgName, counts));
            }
        }

        return html.toString();
    }

    private String generateCountTable(String orgName, List<MessageCount> counts) {
        StringBuilder html = new StringBuilder();

        // Group counts by RSU IP
        Map<String, List<MessageCount>> countsByRsu = counts.stream()
                .collect(Collectors.groupingBy(MessageCount::getRsuIp));

        html.append("<table style=\"border-collapse: collapse; width: 100%; margin-bottom: 20px;\">");

        // Table header
        html.append("<thead><tr style=\"text-align: center; background-color: #b0dfff;\">");
        html.append("<th style=\"padding: 12px; border: 1px solid #ddd;\">RSU</th>");
        html.append("<th style=\"padding: 12px; border: 1px solid #ddd;\">Road</th>");

        for (String messageType : MESSAGE_TYPES) {
            html.append(String.format("<th style=\"padding: 12px; border: 1px solid #ddd;\">%s In</th>", messageType));
            html.append(String.format("<th style=\"padding: 12px; border: 1px solid #ddd;\">%s Out</th>", messageType));
        }
        html.append("</tr></thead><tbody>");

        // Table rows
        boolean styleSwitch = false;
        for (Map.Entry<String, List<MessageCount>> entry : countsByRsu.entrySet()) {
            String rsuIp = entry.getKey();
            List<MessageCount> rsuCounts = entry.getValue();

            String rowStyle = styleSwitch ? "text-align: center; background-color: #f2f2f2;" : "text-align: center;";
            styleSwitch = !styleSwitch;

            html.append(String.format("<tr style=\"%s\">", rowStyle));
            html.append(String.format("<td style=\"border: 1px solid #ddd;\">%s</td>", rsuIp));

            // Get road name from first count (they should all have the same road)
            String road = rsuCounts.isEmpty() ? "Unknown" : rsuCounts.get(0).getRoad();
            html.append(String.format("<td style=\"border: 1px solid #ddd;\">%s</td>", road));

            // Add counts for each message type
            for (String messageType : MESSAGE_TYPES) {
                MessageCount count = rsuCounts.stream()
                        .filter(c -> messageType.equalsIgnoreCase(c.getMessageType()))
                        .findFirst()
                        .orElse(new MessageCount(messageType, rsuIp, 0L, 0L, road));

                html.append(String.format("<td style=\"border: 1px solid #ddd;\">%d</td>",
                        count.getOdeInputCount() != null ? count.getOdeInputCount() : 0L));

                // Calculate color for out count based on deviation
                String outCountColor = calculateOutCountColor(count);
                html.append(String.format("<td style=\"border: 1px solid #ddd; background-color: %s;\">%d</td>",
                        outCountColor, count.getOdeOutputCount() != null ? count.getOdeOutputCount() : 0L));
            }

            html.append("</tr>");
        }

        html.append("</tbody></table>");
        return html.toString();
    }

    private String calculateOutCountColor(MessageCount count) {
        Long inCount = count.getOdeInputCount() != null ? count.getOdeInputCount() : 0L;
        Long outCount = count.getOdeOutputCount() != null ? count.getOdeOutputCount() : 0L;
        String messageType = count.getMessageType();

        if (inCount == 0) {
            return outCount > 0 ? "#ff7373" : "#a4ffa1";
        }

        double deviationPercent;
        if ("BSM".equalsIgnoreCase(messageType) || "TIM".equalsIgnoreCase(messageType)) {
            // For unique deduplication situations, don't validate counts unless zero
            deviationPercent = (inCount != 0 && outCount == 0) || (outCount > inCount) ? 6.0 : 0.0;
        } else {
            // Normalize the diff_percent depending on message types that are deduplicated
            // to 1/hour
            double x = "Map".equalsIgnoreCase(messageType) ? 3600.0 : 1.0;
            deviationPercent = Math.abs((double) outCount / Math.ceil((double) inCount / x) - 1.0) * 100.0;
        }

        return deviationPercent > 5.0 ? "#ff7373" : "#a4ffa1";
    }
}