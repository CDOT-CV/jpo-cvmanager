package us.dot.its.jpo.ode.api.wzdx;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "wzdx-feed")
@ConditionalOnProperty(name = { "enable.api", "enable.wzdx-feed" }, havingValue = "true", matchIfMissing = false)
public class WzdxFeedProperties {

    @NotBlank(message = "Base URL cannot be blank")
    private String baseUrl;

    @NotBlank(message = "WZDX API Key cannot be blank")
    private String apiKey;
}
