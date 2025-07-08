package us.dot.its.jpo.ode.api.services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PrometheusService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String prometheusUrl = "http://localhost:9090";

    public String query(String promQL) {
        String url = UriComponentsBuilder.fromHttpUrl(prometheusUrl)
                .path("/api/v1/query")
                .queryParam("query", promQL)
                .toUriString();

        return restTemplate.getForObject(url, String.class);
    }
}