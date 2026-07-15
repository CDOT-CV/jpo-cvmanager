package us.dot.its.jpo.ode.api.services;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import us.dot.its.jpo.ode.api.wzdx.WzdxFeedProperties;

@Component
public class WzdxService {

    private final WzdxFeedProperties properties;
    private final RestClient restClient;

    public WzdxService(WzdxFeedProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    public String callWzdxApi() {
        var response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/wzdx")
                        .queryParam("apiKey", properties.getApiKey())
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve();

        response.onStatus(HttpStatusCode::isError, (req, res) -> {
            throw new ResponseStatusException(res.getStatusCode(), "Failed to retrieve WZDX API response");
        });

        return response.body(String.class);
    }
}
