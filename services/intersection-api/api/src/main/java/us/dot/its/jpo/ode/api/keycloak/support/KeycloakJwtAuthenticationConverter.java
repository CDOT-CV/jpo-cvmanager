package us.dot.its.jpo.ode.api.keycloak.support;

import lombok.RequiredArgsConstructor;
import us.dot.its.jpo.ode.api.models.keycloak.CvManagerAuthToken;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

/**
 * Converts a JWT into a Spring authentication token (by extracting
 * the username and roles from the claims of the token, delegating
 * to the {@link KeycloakGrantedAuthoritiesConverter})
 */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private Converter<Jwt, Collection<GrantedAuthority>> grantedAuthoritiesConverter;
    private OrganizationRepository organizationRepository;

    public KeycloakJwtAuthenticationConverter(
            Converter<Jwt, Collection<GrantedAuthority>> grantedAuthoritiesConverter,
            OrganizationRepository organizationRepository) {
        this.grantedAuthoritiesConverter = grantedAuthoritiesConverter;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {

        Collection<GrantedAuthority> authorities = grantedAuthoritiesConverter.convert(jwt);
        return convertToCvManagerAuthentication(jwt, authorities);
    }

    protected String getUsernameFrom(Jwt jwt) {

        if (jwt.hasClaim("preferred_username")) {
            return jwt.getClaimAsString("preferred_username");
        }

        return jwt.getSubject();
    }

    protected CvManagerAuthToken convertToCvManagerAuthentication(Jwt jwt,
            Collection<GrantedAuthority> authorities) {

        String username = getUsernameFrom(jwt);
        return new CvManagerAuthToken(jwt, authorities, username, organizationRepository);
    }
}
