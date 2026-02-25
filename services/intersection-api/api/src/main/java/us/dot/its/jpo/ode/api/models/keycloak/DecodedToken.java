package us.dot.its.jpo.ode.api.models.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import us.dot.its.jpo.ode.api.utils.AuthUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Represents a decoded JWT (JSON Web Token) payload from Keycloak.
 * 
 * This class contains standard JWT claims (registered claims) as defined in RFC
 * 7519:
 * https://datatracker.ietf.org/doc/html/rfc7519#section-4.1
 * 
 * And Keycloak-specific claims as documented here:
 * https://www.keycloak.org/docs/latest/server_admin/#_oidc
 * 
 * Note: This class only decodes the token payload. Signature verification
 * should be handled by Spring Security's JWT authentication filters.
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DecodedToken {

    /**
     * Expiration Time (exp) - Registered Claim
     * Unix timestamp when the token expires.
     * 
     * @see <a href=
     *      "https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.4">RFC 7519
     *      Section 4.1.4</a>
     */
    private Long exp;

    /**
     * Issued At (iat) - Registered Claim
     * Unix timestamp when the token was issued.
     * 
     * @see <a href=
     *      "https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.6">RFC 7519
     *      Section 4.1.6</a>
     */
    private Long iat;

    /**
     * Authentication Time (auth_time) - OpenID Connect Standard Claim
     * Unix timestamp when the user authentication occurred.
     * 
     * @see <a href=
     *      "https://openid.net/specs/openid-connect-core-1_0.html#IDToken">OpenID
     *      Connect Core Section 2</a>
     */
    @JsonProperty("auth_time")
    private Long authTime;

    /**
     * JWT ID (jti) - Registered Claim
     * Unique identifier for this JWT token, used to prevent replay attacks.
     * 
     * @see <a href=
     *      "https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.7">RFC 7519
     *      Section 4.1.7</a>
     */
    private String jti;

    /**
     * Issuer (iss) - Registered Claim
     * The URL of the Keycloak realm that issued this token.
     * Example: "http://keycloak-server:8080/realms/cvmanager"
     * 
     * @see <a href=
     *      "https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.1">RFC 7519
     *      Section 4.1.1</a>
     */
    private String iss;

    /**
     * Audience (aud) - Registered Claim
     * The intended recipient(s) of the token. Usually the client ID or "account".
     * 
     * @see <a href=
     *      "https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.3">RFC 7519
     *      Section 4.1.3</a>
     */
    private String aud;

    /**
     * Subject (sub) - Registered Claim
     * Unique identifier for the user (Keycloak user ID).
     * 
     * @see <a href=
     *      "https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.2">RFC 7519
     *      Section 4.1.2</a>
     */
    private String sub;

    /**
     * Token Type (typ) - Header Claim
     * Type of token, typically "Bearer" for OAuth 2.0 access tokens.
     * 
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-5.1">RFC
     *      7519 Section 5.1</a>
     */
    private String typ;

    /**
     * Authorized Party (azp) - OpenID Connect Claim
     * The client ID that was authorized to receive this token.
     * 
     * @see <a href=
     *      "https://openid.net/specs/openid-connect-core-1_0.html#IDToken">OpenID
     *      Connect Core Section 2</a>
     */
    private String azp;

    /**
     * Session ID (sid) - Keycloak-Specific Claim
     * Unique identifier for the user's authentication session in Keycloak.
     * Used for session management and logout.
     */
    private String sid;

    /**
     * Authentication Context Class Reference (acr) - OpenID Connect Claim
     * Level of authentication assurance. "0" typically means no specific level.
     * 
     * @see <a href=
     *      "https://openid.net/specs/openid-connect-core-1_0.html#IDToken">OpenID
     *      Connect Core Section 2</a>
     */
    private String acr;

    /**
     * Allowed Origins - Keycloak-Specific Claim
     * List of allowed CORS origins for this client.
     * Used for browser-based applications to enable cross-origin requests.
     */
    @JsonProperty("allowed-origins")
    private List<String> allowedOrigins;

    /**
     * Realm Access - Keycloak-Specific Claim
     * Contains realm-level roles assigned to the user.
     * These are global roles within the Keycloak realm.
     */
    @JsonProperty("realm_access")
    private RealmAccess realmAccess;

    /**
     * Resource Access - Keycloak-Specific Claim
     * Contains client-specific roles for different resources/clients.
     * Key = client/resource name, Value = roles for that client.
     */
    @JsonProperty("resource_access")
    private Map<String, ResourceAccess> resourceAccess;

    /**
     * Scope - OpenID Connect Claim
     * Space-separated list of OAuth 2.0 scopes granted.
     * Example: "openid email profile"
     * 
     * @see <a href=
     *      "https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims">OpenID
     *      Connect Core Section 5.4</a>
     */
    private String scope;

    /**
     * Email Verified - OpenID Connect Standard Claim
     * Whether the user's email address has been verified.
     * 
     * @see <a href=
     *      "https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims">OpenID
     *      Connect Core Section 5.1</a>
     */
    @JsonProperty("email_verified")
    private Boolean emailVerified;

    /**
     * Name - OpenID Connect Standard Claim
     * User's full name (typically "First Last").
     * 
     * @see <a href=
     *      "https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims">OpenID
     *      Connect Core Section 5.1</a>
     */
    private String name;

    /**
     * Preferred Username - OpenID Connect Standard Claim
     * Username chosen by the user, typically their email or login name.
     * 
     * @see <a href=
     *      "https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims">OpenID
     *      Connect Core Section 5.1</a>
     */
    @JsonProperty("preferred_username")
    private String preferredUsername;

    /**
     * Given Name - OpenID Connect Standard Claim
     * User's first name.
     * 
     * @see <a href=
     *      "https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims">OpenID
     *      Connect Core Section 5.1</a>
     */
    @JsonProperty("given_name")
    private String givenName;

    /**
     * Family Name - OpenID Connect Standard Claim
     * User's last name.
     * 
     * @see <a href=
     *      "https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims">OpenID
     *      Connect Core Section 5.1</a>
     */
    @JsonProperty("family_name")
    private String familyName;

    /**
     * CVManager Data - Custom Application Claim
     * Application-specific data added via Keycloak user attributes or mappers.
     * Contains CVManager-specific user metadata like organization memberships and
     * roles. These are added to the Keycloak token by a custom token mapper, read
     * more about that in /resources/keycloak/README.md
     */
    @JsonProperty("cvmanager_data")
    private CvManagerData cvManagerData;

    /**
     * Email - OpenID Connect Standard Claim
     * User's email address.
     * 
     * @see <a href=
     *      "https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims">OpenID
     *      Connect Core Section 5.1</a>
     */
    private String email;

    /**
     * Decodes a JWT token string into a DecodedToken object, and verifies expiration
     * 
     * ⚠️ SECURITY NOTE: This method does NOT verify the signature - it only decodes
     * the payload.
     * Signature verification MUST be handled by Spring Security's JWT
     * authentication filters.
     * Never use unverified token data for authorization decisions.
     * 
     * @param token The JWT token string (format: header.payload.signature)
     * @return DecodedToken object containing the parsed JWT payload
     * @throws IllegalArgumentException if the token format is invalid or expired
     * @throws RuntimeException         if JSON parsing fails
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC 7519 - JSON
     *      Web Token</a>
     */
    public static DecodedToken fromJwtToken(String token) {
        return fromJwtToken(token, true);
    }

    /**
     * Decodes a JWT token string into a DecodedToken object with optional signature
     * verification.
     * 
     * @param token           The JWT token string (format:
     *                        header.payload.signature)
     * @param checkExpiration Whether to check if the token is expired
     * @return DecodedToken object containing the parsed JWT payload
     * @throws IllegalArgumentException if the token format is invalid, expired, or
     *                                  signature is invalid
     * @throws RuntimeException         if JSON parsing fails
     */
    public static DecodedToken fromJwtToken(String token, boolean checkExpiration) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }

        // Remove "Bearer " prefix if present
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // JWT format: header.payload.signature
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT token format. Expected 3 parts separated by dots.");
        }

        // Decode the payload (second part)
        String payload = parts[1];

        try {
            // Decode and parse the payload
            byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
            String decodedPayload = new String(decodedBytes, StandardCharsets.UTF_8);

            ObjectMapper objectMapper = new ObjectMapper();
            DecodedToken decodedToken = objectMapper.readValue(decodedPayload, DecodedToken.class);

            // Check expiration
            if (checkExpiration) {
                validateExpiration(decodedToken);
            }

            return decodedToken;

        } catch (IllegalArgumentException e) {
            // Re-throw validation exceptions
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode JWT token: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that the token has not expired.
     * 
     * @param token The decoded token
     * @throws IllegalArgumentException if the token is expired
     */
    private static void validateExpiration(DecodedToken token) {
        if (token.getExp() == null) {
            log.warn("Token does not have an expiration claim (exp)");
            return;
        }

        long expirationTime = token.getExp();
        long currentTime = Instant.now().getEpochSecond();

        if (currentTime >= expirationTime) {
            long expiredSeconds = currentTime - expirationTime;
            throw new IllegalArgumentException(
                    String.format("Token has expired %d seconds ago (exp: %d, now: %d)",
                            expiredSeconds, expirationTime, currentTime));
        }

        log.debug("Token is valid, expires in {} seconds", expirationTime - currentTime);
    }

    /**
     * Gets list of organizations where the user has a role at or above the required
     * level.
     * Role hierarchy: ADMIN > OPERATOR > USER
     * 
     * @param requiredRole Minimum required role (e.g., "USER", "OPERATOR", "ADMIN")
     * @return List of organization names where user meets the role requirement
     */
    public List<String> getQualifiedOrgList(String requiredRole) {
        if (cvManagerData == null || cvManagerData.getOrganizations() == null) {
            return List.of();
        }
        return cvManagerData.getOrganizations().stream()
                .filter(entry -> entry != null && AuthUtils.checkRoleAbove(entry.getRole(), requiredRole))
                .map(DecodedToken.CvManagerData.Organization::getOrg)
                .collect(Collectors.toList());
    }

    /**
     * Finds the user's role in a specific organization (case-insensitive).
     * 
     * @param orgName Name of the organization to search for
     * @return Optional containing the role if found, empty otherwise
     */
    public Optional<String> findRoleInOrg(String orgName) {
        if (cvManagerData == null || cvManagerData.getOrganizations() == null) {
            return Optional.empty();
        }
        if (orgName == null) {
            return Optional.empty();
        }
        for (DecodedToken.CvManagerData.Organization org : cvManagerData.getOrganizations()) {
            if (org == null) {
                continue;
            }
            String organizationName = org.getOrg();
            if (organizationName != null && organizationName.equalsIgnoreCase(orgName)) {
                return Optional.ofNullable(org.getRole());
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if the user is a super user (has global admin privileges).
     * 
     * @return true if super_user is "1", false otherwise
     */
    public boolean isSuperUser() {
        if (cvManagerData == null) {
            return false;
        }
        String superUser = cvManagerData.getSuperUser();
        return "1".equals(superUser);
    }

    /**
     * Realm-level roles assigned to the user.
     * These are global roles within the Keycloak realm.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RealmAccess {
        private List<String> roles;
    }

    /**
     * Client-specific roles for a particular resource/application.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceAccess {
        private List<String> roles;
    }

    /**
     * CVManager-specific user data stored in Keycloak.
     * This is a custom claim added via Keycloak user attributes or protocol
     * mappers.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CvManagerData {

        /**
         * Super user flag. "1" = super user, "0" or null = regular user.
         * Super users have unrestricted access across all organizations.
         */
        @JsonProperty("super_user")
        private String superUser;

        /**
         * List of organizations the user belongs to, with their role in each.
         */
        private List<Organization> organizations;

        /**
         * Unix timestamp (milliseconds) when the user account was created.
         */
        @JsonProperty("user_created_timestamp")
        private Long userCreatedTimestamp;

        /**
         * Represents a user's membership in an organization with an assigned role.
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Organization {
            /**
             * Organization name (e.g., "CDOT", "WYDOT")
             */
            private String org;

            /**
             * User's role in this organization (e.g., "admin", "operator", "user")
             */
            private String role;
        }
    }
}
