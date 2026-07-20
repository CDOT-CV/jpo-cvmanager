package us.dot.its.jpo.ode.api.services;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.wzdx.WzdxFeedProperties;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = { "enable.api", "enable.wzdx-feed" }, havingValue = "true")
public class WzdxService {

    private final WzdxFeedProperties properties;
    private final RestTemplate restTemplate;

    public String callWzdxApi() {
        log.debug("WzdxFeed GET requested");

        URI uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/api/v1/wzdx")
                .queryParam("apiKey", properties.getApiKey())
                .build()
                .toUri();

        ResponseEntity<String> response;
        try {
            response = restTemplate.getForEntity(uri, String.class);

            if (response.getStatusCode().isError()) {
                log.error("WZDX GET request failed with status code {}", response.getStatusCode());
                throw new ResponseStatusException(response.getStatusCode(), "WZDX GET request failed");
            }

            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to retrieve WZDX API response", e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to retrieve WZDX API response", e);
        }
    }
}
