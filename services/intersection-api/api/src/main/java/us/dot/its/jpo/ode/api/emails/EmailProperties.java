package us.dot.its.jpo.ode.api.emails;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.postmarkapp.postmark.Postmark;
import com.postmarkapp.postmark.client.ApiClient;
import com.sendgrid.SendGrid;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.emails.EmailBrokerType;

@Slf4j
@Component
@ConfigurationProperties(prefix = "email")
@Data
public class EmailProperties {
    private EmailBrokerType broker;
    private String senderAddress;
    private String unsubscribeSecretKey;
    private String cvmgrFrontEndUri;

    private SendgridProperties sendgrid;
    private PostmarkProperties postmark;

    @Bean
    SendGrid sendGrid() {
        return new SendGrid(sendgrid.getApiKey());
    }

    @Bean
    ApiClient apiClient() {
        return Postmark.getApiClient(postmark.getApiKey());
    }

    public void setCvmgrFrontEndUri(String cvmgrFrontEndUri) {
        if (cvmgrFrontEndUri != null) {
            this.cvmgrFrontEndUri = cvmgrFrontEndUri.replaceAll("/$", "");
        } else {
            this.cvmgrFrontEndUri = null;
        }
    }

    /**
     * Sets the email broker type based on the provided string.
     * 
     * @param broker the string representation of the broker type
     *               If the string is null, empty, or does not match any
     *               EmailBrokerType, it defaults to SMTP.
     */
    public void setBroker(String broker) {
        if (broker == null || broker.trim().isEmpty()) {
            this.broker = EmailBrokerType.SMTP; // Default to SMTP if no broker is specified
            log.info("Email broker not specified, defaulting to SMTP.");
            return;
        }
        try {
            this.broker = EmailBrokerType.valueOf(broker.trim().toUpperCase());
            log.info("Email broker set to : {}", this.broker);
        } catch (IllegalArgumentException e) {
            // If the value doesn't match any EmailBrokerType, default to SMTP
            this.broker = EmailBrokerType.SMTP;
            log.warn("Invalid email broker type '{}', defaulting to SMTP.", broker);
        }
    }

    @Data
    public static class SendgridProperties {
        private String apiKey;
    }

    @Data
    public static class PostmarkProperties {
        private String apiKey;
    }
}
