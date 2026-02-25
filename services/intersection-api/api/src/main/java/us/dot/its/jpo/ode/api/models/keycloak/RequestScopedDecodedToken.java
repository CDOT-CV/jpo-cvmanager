package us.dot.its.jpo.ode.api.models.keycloak;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestScopedDecodedToken {
    private DecodedToken token;
    private String jwtToken;
    private boolean initialized = false;

    public DecodedToken getToken(String jwtToken) {
        if (!initialized) {
            this.jwtToken = jwtToken;
            this.token = DecodedToken.fromJwtToken(jwtToken);
            this.initialized = true;
        } else if (this.jwtToken != null && jwtToken != null && !this.jwtToken.equals(jwtToken)) {
            throw new IllegalStateException("RequestScopedDecodedToken.getToken called with a different JWT than the one already initialized for this request.");
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