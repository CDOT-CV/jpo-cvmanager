package us.dot.its.jpo.ode.api.models.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class TokenPostRequestKeycloak {
    @JsonProperty("client_id")
    private String clientId;
    @JsonProperty("client_secret")
    private String clientSecret;
    @JsonProperty("grant_type")
    private String grantType;
    @JsonProperty("username")
    private String username;
    @JsonProperty("password")
    private String password;
    @JsonProperty("scope")
    private String scope;

    public TokenPostRequestKeycloak(TokenPostRequest request, String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.grantType = "password";
        this.username = request.getUsername();
        this.password = request.getPassword();
        this.scope = "openid";
    }

    public TokenPostRequestKeycloak(TokenPostRequestServiceAccount request) {
        this.clientId = request.getClientId();
        this.clientSecret = request.getClientSecret();
        this.grantType = "client_credentials";
        this.scope = "openid";
    }

    public MultiValueMap<String, String> getFormData() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("grant_type", grantType);
        formData.add("scope", scope);
        formData.add("username", username);
        formData.add("password", password);
        return formData;
    }
}
