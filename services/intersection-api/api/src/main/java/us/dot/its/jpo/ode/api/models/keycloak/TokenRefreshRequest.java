package us.dot.its.jpo.ode.api.models.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Data;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class TokenRefreshRequest {
    @JsonProperty("refresh_token")
    private String refreshToken;
}
