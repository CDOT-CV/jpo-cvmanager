package us.dot.its.jpo.ode.api.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import us.dot.its.jpo.ode.api.keycloak.KeycloakApi;
import us.dot.its.jpo.ode.api.models.keycloak.TokenPostRequest;
import us.dot.its.jpo.ode.api.models.keycloak.TokenPostRequestServiceAccount;
import us.dot.its.jpo.ode.api.models.keycloak.TokenPostResponse;
import us.dot.its.jpo.ode.api.models.keycloak.TokenRefreshRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@ApiResponses(value = {
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
})
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final KeycloakApi keycloakApi;

    @Operation(summary = "Generate Keycloak Token", description = "Generate Keycloak token for a user")
    @PostMapping(value = "/token", produces = "application/json")
    public Mono<TokenPostResponse> generateToken(@RequestBody TokenPostRequest request) {
        return keycloakApi.generateKeycloakToken(request);
    }

    @Operation(summary = "Generate Keycloak Token for Service Account", description = "Generate Keycloak token for a service account")
    @PostMapping(value = "/token-service-account", produces = "application/json")
    public Mono<TokenPostResponse> generateTokenForServiceAccount(@RequestBody TokenPostRequestServiceAccount request) {
        return keycloakApi.generateKeycloakTokenServiceAccount(request);
    }

    @Operation(summary = "Refresh Keycloak Token", description = "Refresh Keycloak token for a user")
    @PostMapping(value = "/refresh", produces = "application/json")
    public Mono<TokenPostResponse> refreshToken(@RequestBody TokenRefreshRequest request) {
        return keycloakApi.refreshKeycloakToken(request);
    }

}