package us.dot.its.jpo.ode.api.keycloak;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.models.keycloak.TokenPostRequest;
import us.dot.its.jpo.ode.api.models.keycloak.TokenPostRequestKeycloak;
import us.dot.its.jpo.ode.api.models.keycloak.TokenPostRequestServiceAccount;
import us.dot.its.jpo.ode.api.models.keycloak.TokenPostResponse;
import us.dot.its.jpo.ode.api.models.keycloak.TokenRefreshRequest;
import us.dot.its.jpo.ode.api.models.keycloak.TokenRefreshRequestKeycloak;
import us.dot.its.jpo.ode.api.keycloak.config.KeycloakAdminConfig;

@Slf4j
@Service
public class KeycloakApi {
    private final String keycloakRealm;
    private final String keycloakClientId;
    private final String keycloakClientSecret;

    private final WebClient webClient;

    public KeycloakApi(KeycloakAdminConfig keycloakProperties, WebClient.Builder webClientBuilder) {
        this.keycloakRealm = keycloakProperties.getRealm();
        this.keycloakClientId = keycloakProperties.getClientId();
        this.keycloakClientSecret = keycloakProperties.getClientSecret();
        this.webClient = webClientBuilder.baseUrl(keycloakProperties.getAuthServer()).build();
    }

    public Mono<TokenPostResponse> generateKeycloakToken(TokenPostRequest request) {
        TokenPostRequestKeycloak requestBody = new TokenPostRequestKeycloak(request, keycloakClientId,
                keycloakClientSecret);

        return webClient.post()
                .uri(String.format("/realms/%s/protocol/openid-connect/token", keycloakRealm))
                .headers(headers -> {
                    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
                })
                .body(BodyInserters.fromFormData(requestBody.getFormData()))
                .exchangeToMono(response -> switch (response.statusCode()) {
                    case HttpStatus.OK -> response.bodyToMono(TokenPostResponse.class);
                    default -> {
                        log.warn("Received non-success error code: {}", response.statusCode());
                        yield response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new ResponseStatusException(response.statusCode(),
                                        String.format("Keycloak returned error: %s", body))));
                    }
                });
    }

    public Mono<TokenPostResponse> generateKeycloakTokenServiceAccount(TokenPostRequestServiceAccount request) {
        TokenPostRequestKeycloak requestBody = new TokenPostRequestKeycloak(request);

        return webClient.post()
                .uri(String.format("/realms/%s/protocol/openid-connect/token", keycloakRealm))
                .headers(headers -> {
                    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
                })
                .body(BodyInserters.fromFormData(requestBody.getFormData()))
                .exchangeToMono(response -> switch (response.statusCode()) {
                    case HttpStatus.OK -> response.bodyToMono(TokenPostResponse.class);
                    default -> {
                        log.warn("Received non-success error code: {}", response.statusCode());
                        yield response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new ResponseStatusException(response.statusCode(),
                                        String.format("Keycloak returned error: %s", body))));
                    }
                });
    }

    public Mono<TokenPostResponse> refreshKeycloakToken(TokenRefreshRequest request) {
        TokenRefreshRequestKeycloak requestBody = new TokenRefreshRequestKeycloak(request, keycloakClientId,
                keycloakClientSecret);

        return webClient.post()
                .uri(String.format("/realms/%s/protocol/openid-connect/token", keycloakRealm))
                .headers(headers -> {
                    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
                })
                .body(BodyInserters.fromFormData(requestBody.getFormData()))
                .exchangeToMono(response -> switch (response.statusCode()) {
                    case HttpStatus.OK -> response.bodyToMono(TokenPostResponse.class);
                    default -> {
                        log.warn("Received non-success error code: {}", response.statusCode());
                        yield response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new ResponseStatusException(response.statusCode(),
                                        String.format("Keycloak returned error: %s", body))));
                    }
                });
    }
}