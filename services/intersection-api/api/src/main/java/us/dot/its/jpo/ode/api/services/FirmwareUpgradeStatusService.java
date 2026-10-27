package us.dot.its.jpo.ode.api.services;

import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import us.dot.its.jpo.ode.api.models.devices.management.FirmwareUpgradeStatus;

@Service
public class FirmwareUpgradeStatusService {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestTemplate client;

    @Value("${firmwareManagerEndpoint:}")
    private String endpoint;

    public FirmwareUpgradeStatusService(RestTemplateBuilder builder) {
        // Allow 5 seconds to connect and 10 seconds to wait for response data.
        // These are separate timeouts, not a single overall request deadline.
        client = builder.connectTimeout(CONNECT_TIMEOUT).readTimeout(READ_TIMEOUT).build();
    }

    public FirmwareUpgradeStatus getStatus() {
        if (endpoint == null || endpoint.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Firmware manager is not configured");
        }
        try {
            var uri = UriComponentsBuilder.fromUriString(endpoint).pathSegment("list_active_upgrades").build().toUri();
            var status = client.getForObject(uri, FirmwareUpgradeStatus.class);
            if (status == null || status.activeUpgrades() == null || status.upgradeQueue() == null
                    || status.activeUpgrades().values().stream().anyMatch(Objects::isNull)
                    || status.upgradeQueue().stream().anyMatch(Objects::isNull)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Firmware manager returned an invalid status response");
            }
            return status;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to retrieve firmware upgrade status", ex);
        }
    }
}
