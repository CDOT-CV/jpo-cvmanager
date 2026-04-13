package us.dot.its.jpo.ode.api.models.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Data;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class TokenPostRequest {
    @Schema(description = "Username for the token request")
    @JsonProperty("username")
    private String username;
    @Schema(description = "Password for the token request")
    @JsonProperty("password")
    private String password;
}
