package us.dot.its.jpo.ode.api.models.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import us.dot.its.jpo.ode.api.services.PermissionService;
import lombok.AllArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DecodedToken {

    private Long exp;
    private Long iat;

    @JsonProperty("auth_time")
    private Long authTime;

    private String jti;
    private String iss;
    private String aud;
    private String sub;
    private String typ;
    private String azp;
    private String sid;
    private String acr;

    @JsonProperty("allowed-origins")
    private List<String> allowedOrigins;

    @JsonProperty("realm_access")
    private RealmAccess realmAccess;

    @JsonProperty("resource_access")
    private Map<String, ResourceAccess> resourceAccess;

    private String scope;

    @JsonProperty("email_verified")
    private Boolean emailVerified;

    private String name;

    @JsonProperty("preferred_username")
    private String preferredUsername;

    @JsonProperty("given_name")
    private String givenName;

    @JsonProperty("family_name")
    private String familyName;

    @JsonProperty("cvmanager_data")
    private CvManagerData cvManagerData;

    private String email;

    /**
     * Decodes a JWT token string into a DecodedToken object.
     * Note: This method does NOT verify the signature - it only decodes the
     * payload.
     * Signature verification should be handled by your authentication framework.
     * 
     * @param token The JWT token string (format: header.payload.signature)
     * @return DecodedToken object containing the parsed JWT payload
     * @throws IllegalArgumentException if the token format is invalid
     * @throws RuntimeException         if JSON parsing fails
     */
    public static DecodedToken fromJwtToken(String token) {
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
            // Base64 decode
            byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
            String decodedPayload = new String(decodedBytes, StandardCharsets.UTF_8);

            // Parse JSON to DecodedToken object
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(decodedPayload, DecodedToken.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to decode JWT token: " + e.getMessage(), e);
        }
    }

    public List<String> getQualifiedOrgList(String requiredRole) {
        if (cvManagerData == null || cvManagerData.getOrganizations() == null) {
            return List.of();
        }
        return cvManagerData.getOrganizations().stream()
                .filter(entry -> PermissionService.checkRoleAbove(entry.getRole(), requiredRole))
                .map(DecodedToken.CvManagerData.Organization::getOrg)
                .collect(Collectors.toList());
    }

    public Optional<String> findRoleInOrg(String orgName) {
        if (cvManagerData == null || cvManagerData.getOrganizations() == null) {
            return Optional.empty();
        }
        for (DecodedToken.CvManagerData.Organization org : cvManagerData.getOrganizations()) {
            if (orgName != null && org != null && org.getOrg() != null && org.getOrg().equalsIgnoreCase(orgName)) {
                return Optional.of(org.getRole());
            }
        }
        return Optional.empty();
    }

    public boolean isSuperUser() {
        return cvManagerData != null && "1".equals(cvManagerData.getSuperUser());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RealmAccess {
        private List<String> roles;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceAccess {
        private List<String> roles;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CvManagerData {

        @JsonProperty("super_user")
        private String superUser;

        private List<Organization> organizations;

        @JsonProperty("user_created_timestamp")
        private Long userCreatedTimestamp;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Organization {
            private String org;
            private String role;
        }
    }
}
