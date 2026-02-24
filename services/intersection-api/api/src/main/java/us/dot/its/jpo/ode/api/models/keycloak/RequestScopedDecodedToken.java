package us.dot.its.jpo.ode.api.models.keycloak;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestScopedDecodedToken {
    private DecodedToken token;
    private boolean initialized = false;

    public DecodedToken getToken(String jwtToken) {
        if (!initialized) {
            this.token = DecodedToken.fromJwtToken(jwtToken);
            this.initialized = true;
        }
        return this.token;
    }

    public DecodedToken getTokenIfPresent() {
        return this.token;
    }

    public boolean isInitialized() {
        return this.initialized;
    }
}