package us.dot.its.jpo.ode.api.emails;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.postmarkapp.postmark.Postmark;
import com.postmarkapp.postmark.client.ApiClient;
import com.sendgrid.Client;
import com.sendgrid.SendGrid;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.emails.EmailBrokerType;

@Slf4j
@Component
@Configuration
@ConfigurationProperties(prefix = "email")
@Data
public class EmailProperties {
    private static final int SENDGRID_HEALTH_CHECK_TIMEOUT_MILLIS = 5_000;
    private static final int POSTMARK_HEALTH_CHECK_TIMEOUT_SECONDS = 5;

    private EmailBrokerType broker;
    private String senderAddress;
    private String unsubscribeSecretKey;
    private String cvmgrFrontEndUri;

    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private String smtpPassword;

    private SendgridProperties sendgrid;
    private PostmarkProperties postmark;

    @Bean
    @Primary
    @ConditionalOnProperty(name = "email.broker", havingValue = "SENDGRID")
    SendGrid sendGrid() {
        return new SendGrid(sendgrid.getApiKey());
    }

    @Bean("sendGridHealthCheck")
    @ConditionalOnProperty(name = "email.broker", havingValue = "SENDGRID")
    SendGrid sendGridHealthCheck() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(SENDGRID_HEALTH_CHECK_TIMEOUT_MILLIS)
                .setConnectTimeout(SENDGRID_HEALTH_CHECK_TIMEOUT_MILLIS)
                .setSocketTimeout(SENDGRID_HEALTH_CHECK_TIMEOUT_MILLIS)
                .build();
        Client client = new Client(HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build());
        return new SendGrid(sendgrid.getApiKey(), client);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "email.broker", havingValue = "POSTMARK")
    ApiClient apiClient() {
        return Postmark.getApiClient(postmark.getApiKey());
    }

    @Bean("postmarkHealthCheck")
    @ConditionalOnProperty(name = "email.broker", havingValue = "POSTMARK")
    ApiClient postmarkHealthCheck() {
        ApiClient client = Postmark.getApiClient(postmark.getApiKey());
        client.setConnectTimeoutSeconds(POSTMARK_HEALTH_CHECK_TIMEOUT_SECONDS);
        client.setReadTimeoutSeconds(POSTMARK_HEALTH_CHECK_TIMEOUT_SECONDS);
        return client;
    }

    public void setCvmgrFrontEndUri(String cvmgrFrontEndUri) {
        if (cvmgrFrontEndUri != null) {
            this.cvmgrFrontEndUri = cvmgrFrontEndUri.replaceAll("/$", "");
        } else {
            this.cvmgrFrontEndUri = null;
        }
    }

    /**
     * Sets the email broker type based on the provided string
     * 
     * @param broker the string representation of the broker type
     *               If the string is null, empty, or does not match any
     *               EmailBrokerType, it defaults to SMTP. If the broker is not
     *               one of the allowed values, an exception will be thrown.
     */
    public void setBroker(String broker) {
        if (broker == null || broker.trim().isEmpty()) {
            this.broker = EmailBrokerType.SMTP; // Default to SMTP if no broker is specified
            log.info("Email broker not specified, defaulting to SMTP.");
            return;
        }
        this.broker = EmailBrokerType.valueOf(broker); // Matching @Qualifier from EmailProvider classes
        log.info("Email broker set to : {}", this.broker);
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
