package us.dot.its.jpo.ode.api.wzdx;


import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;

@Validated
public class WzdxFeedProperties {

    @NotBlank(message = "Base URL cannot be blank")
    private String baseUrl;

    @NotBlank(message = "WZDX API Key cannot be blank")
    private String apiKey;

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }
}
