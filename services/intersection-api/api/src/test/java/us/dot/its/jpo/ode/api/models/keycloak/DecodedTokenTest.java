package us.dot.its.jpo.ode.api.models.keycloak;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

class DecodedTokenTest {

    private static final String VALID_JWT_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICIyZ0tGZkF2S0pqWFQ4NDdwZG5aYWl4TWlTWERBVm5ZMVVmb2pjZXc2UGZRIn0.eyJleHAiOjE3NzE2MTUxNjMsImlhdCI6MTc3MTYxMzM2MywiYXV0aF90aW1lIjoxNzcxNjEzMjAwLCJqdGkiOiI3OTNkODU2OC05ZGU5LTRkNTktYWRiNy0wYTY1MGNiNTczNWYiLCJpc3MiOiJodHRwOi8vMTkyLjE2OC4xMTAuMTQ3OjgwODQvcmVhbG1zL2N2bWFuYWdlciIsImF1ZCI6ImFjY291bnQiLCJzdWIiOiJmYzNkODcyOS04NTI2LTRhYWEtODA1Yi1kNjRiZjNiOTM4NjAiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJjdm1hbmFnZXItZ3VpIiwic2lkIjoiYzI2NjQwOGItNDc5Mi00YmRlLThhMjMtOGZmZWY2ODdmYTc4IiwiYWNyIjoiMCIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwOi8vbG9jYWxob3N0OjMwMDIiLCJodHRwOi8vbG9jYWxob3N0IiwiaHR0cDovL2xvY2FsaG9zdDozMDAxIiwiaHR0cDovL2xvY2FsaG9zdDozMDAwIl0sInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJvZmZsaW5lX2FjY2VzcyIsInVtYV9hdXRob3JpemF0aW9uIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJvcGVuaWQgZW1haWwgcHJvZmlsZSIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwibmFtZSI6IlRlc3QgVXNlciIsInByZWZlcnJlZF91c2VybmFtZSI6InRlc3RAZ21haWwuY29tIiwiZ2l2ZW5fbmFtZSI6IlRlc3QiLCJmYW1pbHlfbmFtZSI6IlVzZXIiLCJjdm1hbmFnZXJfZGF0YSI6eyJzdXBlcl91c2VyIjoiMSIsIm9yZ2FuaXphdGlvbnMiOlt7Im9yZyI6IlRlc3QgT3JnIiwicm9sZSI6ImFkbWluIn0seyJvcmciOiJUZXN0IE9yZyAyIiwicm9sZSI6InVzZXIifV0sInVzZXJfY3JlYXRlZF90aW1lc3RhbXAiOjE3NDY3NzM1MjcyODN9LCJlbWFpbCI6InRlc3RAZ21haWwuY29tIn0.ShaxFDStayytuwgLsOxHrkObesk9sP5tguxDjD0_AEz6X2lJivLoqy6PHzX4lQv7-oLU7LLMDj8Tdmpeai5wEr1Zxq7daXTH37z2HHLpeUrHw88gZ0Wor2CUACNzlKi69H9cxSEkC_sLe80Gv3GfKj9H-JPFHOiJV6qHVcTqzz14pARR0TdjyHpIFbARY03Wivj7PJmVL3c3G26Do5JFYVeRwPmqb8a8H4KGf_8iBC7t1QPqP3mq7_OCfObJHyNoI1tRsF-KhVW5thJUPPkKRkdEYCAHlkMHIIPnOTfmsOrRn9zqFzrjED5ickOeQAxXzHn40rywXch4iVas4S_GqQ";

    @Nested
    @DisplayName("Tests for fromJwtToken constructor")
    class FromJwtTokenTests {
        private static final long VALID_TOKEN_TIMESTAMP = 1771615133L; // 30 seconds before expiration

        @Test
        void testFromJwtToken_ValidToken() {
            Instant fixedInstant = Instant.ofEpochSecond(VALID_TOKEN_TIMESTAMP);
            try (MockedStatic<Instant> mockedInstant = Mockito.mockStatic(Instant.class)) {
                mockedInstant.when(Instant::now).thenReturn(fixedInstant);

                DecodedToken token = DecodedToken.fromJwtToken(VALID_JWT_TOKEN);
                            // Verify basic JWT fields
            assertNotNull(token);
            assertEquals(1771615163L, token.getExp());
            assertEquals(1771613363L, token.getIat());
            assertEquals(1771613200L, token.getAuthTime());
            assertEquals("793d8568-9de9-4d59-adb7-0a650cb5735f", token.getJti());
            assertEquals("http://192.168.110.147:8084/realms/cvmanager", token.getIss());
            assertEquals("account", token.getAud());
            assertEquals("fc3d8729-8526-4aaa-805b-d64bf3b93860", token.getSub());
            assertEquals("Bearer", token.getTyp());
            assertEquals("cvmanager-gui", token.getAzp());
            assertEquals("c266408b-4792-4bde-8a23-8ffef687fa78", token.getSid());
            assertEquals("0", token.getAcr());

            // Verify allowed origins
            assertNotNull(token.getAllowedOrigins());
            assertEquals(4, token.getAllowedOrigins().size());
            assertTrue(token.getAllowedOrigins().contains("http://localhost:3000"));
            assertTrue(token.getAllowedOrigins().contains("http://localhost:3001"));
            assertTrue(token.getAllowedOrigins().contains("http://localhost:3002"));
            assertTrue(token.getAllowedOrigins().contains("http://localhost"));

            // Verify realm access
            assertNotNull(token.getRealmAccess());
            assertNotNull(token.getRealmAccess().getRoles());
            assertEquals(2, token.getRealmAccess().getRoles().size());
            assertTrue(token.getRealmAccess().getRoles().contains("offline_access"));
            assertTrue(token.getRealmAccess().getRoles().contains("uma_authorization"));

            // Verify resource access
            assertNotNull(token.getResourceAccess());
            assertTrue(token.getResourceAccess().containsKey("account"));
            assertEquals(3, token.getResourceAccess().get("account").getRoles().size());
            assertTrue(token.getResourceAccess().get("account").getRoles().contains("manage-account"));
            assertTrue(token.getResourceAccess().get("account").getRoles().contains("manage-account-links"));
            assertTrue(token.getResourceAccess().get("account").getRoles().contains("view-profile"));

            // Verify scope
            assertEquals("openid email profile", token.getScope());

            // Verify user information
            assertEquals(false, token.getEmailVerified());
            assertEquals("Test User", token.getName());
            assertEquals("test@gmail.com", token.getPreferredUsername());
            assertEquals("Test", token.getGivenName());
            assertEquals("User", token.getFamilyName());
            assertEquals("test@gmail.com", token.getEmail());

            // Verify CVManager data
            assertNotNull(token.getCvManagerData());
            assertEquals("1", token.getCvManagerData().getSuperUser());
            assertEquals(1746773527283L, token.getCvManagerData().getUserCreatedTimestamp());

            // Verify organizations
            assertNotNull(token.getCvManagerData().getOrganizations());
            assertEquals(2, token.getCvManagerData().getOrganizations().size());

            DecodedToken.CvManagerData.Organization org1 = token.getCvManagerData().getOrganizations().get(0);
            assertEquals("Test Org", org1.getOrg());
            assertEquals("admin", org1.getRole());

            DecodedToken.CvManagerData.Organization org2 = token.getCvManagerData().getOrganizations().get(1);
            assertEquals("Test Org 2", org2.getOrg());
            assertEquals("user", org2.getRole());
            }
        }

        @Test
        void testFromJwtToken_Expired() {
            String tokenWithBearer = "Bearer " + VALID_JWT_TOKEN;
            assertThrows(IllegalArgumentException.class, () -> DecodedToken.fromJwtToken(tokenWithBearer, true));
        }

        @Test
        void testFromJwtToken_WithBearerPrefix() {
            String tokenWithBearer = "Bearer " + VALID_JWT_TOKEN;
            DecodedToken token = DecodedToken.fromJwtToken(tokenWithBearer, false);

            assertNotNull(token);
            assertEquals("test@gmail.com", token.getEmail());
            assertEquals("Test User", token.getName());
        }

        @Test
        void testFromJwtToken_NullToken() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DecodedToken.fromJwtToken(null, false));
            assertEquals("Token cannot be null or empty", exception.getMessage());
        }

        @Test
        void testFromJwtToken_EmptyToken() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DecodedToken.fromJwtToken("", false));
            assertEquals("Token cannot be null or empty", exception.getMessage());
        }

        @Test
        void testFromJwtToken_WhitespaceToken() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DecodedToken.fromJwtToken("   ", false));
            assertEquals("Token cannot be null or empty", exception.getMessage());
        }

        @Test
        void testFromJwtToken_InvalidFormat_TwoParts() {
            String invalidToken = "header.payload";
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DecodedToken.fromJwtToken(invalidToken, false));
            assertEquals("Invalid JWT token format. Expected 3 parts separated by dots.", exception.getMessage());
        }

        @Test
        void testFromJwtToken_InvalidFormat_FourParts() {
            String invalidToken = "header.payload.signature.extra";
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DecodedToken.fromJwtToken(invalidToken, false));
            assertEquals("Invalid JWT token format. Expected 3 parts separated by dots.", exception.getMessage());
        }

        @Test
        void testFromJwtToken_InvalidBase64() {
            String invalidToken = "header.not-valid-base64!@#$.signature";
            assertThrows(RuntimeException.class, () -> DecodedToken.fromJwtToken(invalidToken, false));
        }

        @Test
        void testFromJwtToken_InvalidJson() {
            // Valid Base64 but invalid JSON
            String invalidToken = "header.aW52YWxpZCBqc29u.signature"; // "invalid json" in base64
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> DecodedToken.fromJwtToken(invalidToken, false));
            assertTrue(exception.getMessage().contains("Failed to decode JWT token"));
        }
    }

    @Nested
    @DisplayName("Tests for getQualifiedOrgList method")
    class GetQualifiedOrgListTests {

        @Test
        void testGetQualifiedOrgList_AdminRole() {
            DecodedToken token = DecodedToken.fromJwtToken(VALID_JWT_TOKEN, false);

            List<String> qualifiedOrgs = token.getQualifiedOrgList("ADMIN");

            assertNotNull(qualifiedOrgs);
            assertEquals(1, qualifiedOrgs.size());
            assertTrue(qualifiedOrgs.contains("Test Org"));
            assertFalse(qualifiedOrgs.contains("Test Org 2")); // user role is below admin
        }

        @Test
        void testGetQualifiedOrgList_UserRole() {
            DecodedToken token = DecodedToken.fromJwtToken(VALID_JWT_TOKEN, false);

            List<String> qualifiedOrgs = token.getQualifiedOrgList("USER");

            assertNotNull(qualifiedOrgs);
            assertEquals(2, qualifiedOrgs.size());
            assertTrue(qualifiedOrgs.contains("Test Org")); // admin is above user
            assertTrue(qualifiedOrgs.contains("Test Org 2")); // exact match
        }

        @Test
        void testGetQualifiedOrgList_OperatorRole() {
            DecodedToken token = DecodedToken.fromJwtToken(VALID_JWT_TOKEN, false);

            List<String> qualifiedOrgs = token.getQualifiedOrgList("OPERATOR");

            assertNotNull(qualifiedOrgs);
            assertEquals(1, qualifiedOrgs.size());
            assertTrue(qualifiedOrgs.contains("Test Org"));
            assertFalse(qualifiedOrgs.contains("Test Org 2"));
        }
    }

    @Nested
    @DisplayName("Tests for findRoleInOrg method")
    class FindRoleInOrgTests {
        @Test
        void testFindRoleInOrg_ExistingOrg() {
            DecodedToken token = DecodedToken.fromJwtToken(VALID_JWT_TOKEN, false);

            Optional<String> role = token.findRoleInOrg("Test Org");

            assertTrue(role.isPresent());
            assertEquals("admin", role.get());
        }

        @Test
        void testFindRoleInOrg_ExistingOrgCaseInsensitive() {
            DecodedToken token = DecodedToken.fromJwtToken(VALID_JWT_TOKEN, false);

            Optional<String> role = token.findRoleInOrg("test org");

            assertTrue(role.isPresent());
            assertEquals("admin", role.get());
        }

        @Test
        void testFindRoleInOrg_NonExistingOrg() {
            DecodedToken token = DecodedToken.fromJwtToken(VALID_JWT_TOKEN, false);

            Optional<String> role = token.findRoleInOrg("Nonexistent Org");

            assertFalse(role.isPresent());
        }

        @Test
        void testFindRoleInOrg_SecondOrganization() {
            DecodedToken token = DecodedToken.fromJwtToken(VALID_JWT_TOKEN, false);

            Optional<String> role = token.findRoleInOrg("Test Org 2");

            assertTrue(role.isPresent());
            assertEquals("user", role.get());
        }
    }

    @Nested
    @DisplayName("Tests for DecodedToken Data and Nested Classes")
    class DecodedTokenDataTests {

        @Test
        void testDecodedToken_AllArgsConstructor() {
            DecodedToken.RealmAccess realmAccess = new DecodedToken.RealmAccess(
                    Arrays.asList("role1", "role2"));

            DecodedToken.CvManagerData.Organization org = new DecodedToken.CvManagerData.Organization(
                    "Test Org",
                    "admin");

            DecodedToken.CvManagerData cvManagerData = new DecodedToken.CvManagerData(
                    "1",
                    Arrays.asList(org),
                    1746773527283L);

            DecodedToken token = new DecodedToken();
            token.setEmail("test@example.com");
            token.setName("Test User");
            token.setRealmAccess(realmAccess);
            token.setCvManagerData(cvManagerData);

            assertEquals("test@example.com", token.getEmail());
            assertEquals("Test User", token.getName());
            assertEquals(2, token.getRealmAccess().getRoles().size());
            assertEquals("1", token.getCvManagerData().getSuperUser());
            assertEquals(1, token.getCvManagerData().getOrganizations().size());
        }

        @Test
        void testResourceAccess_NestedClass() {
            DecodedToken.ResourceAccess resourceAccess = new DecodedToken.ResourceAccess(
                    Arrays.asList("manage-account", "view-profile"));

            assertEquals(2, resourceAccess.getRoles().size());
            assertTrue(resourceAccess.getRoles().contains("manage-account"));
            assertTrue(resourceAccess.getRoles().contains("view-profile"));
        }

        @Test
        void testCvManagerDataOrganization_NestedClass() {
            DecodedToken.CvManagerData.Organization org = new DecodedToken.CvManagerData.Organization();
            org.setOrg("My Org");
            org.setRole("operator");

            assertEquals("My Org", org.getOrg());
            assertEquals("operator", org.getRole());
        }

        @Test
        void testGetQualifiedOrgList_EmptyOrganizations() {
            DecodedToken token = new DecodedToken();
            DecodedToken.CvManagerData cvManagerData = new DecodedToken.CvManagerData(
                    "0",
                    Arrays.asList(),
                    System.currentTimeMillis());
            token.setCvManagerData(cvManagerData);

            List<String> qualifiedOrgs = token.getQualifiedOrgList("USER");

            assertNotNull(qualifiedOrgs);
            assertEquals(0, qualifiedOrgs.size());
        }
    }

    @Nested
    @DisplayName("Tests for isSuperUser method")
    class IsSuperUserTests {

        @Test
        void testIsSuperUser_True() {
            DecodedToken token = new DecodedToken();
            token.setCvManagerData(new DecodedToken.CvManagerData("1", Arrays.asList(), 1746773527283L));

            assertTrue(token.isSuperUser());
        }

        @Test
        void testIsSuperUser_False() {
            DecodedToken token = new DecodedToken();
            token.setCvManagerData(new DecodedToken.CvManagerData("0", Arrays.asList(), 1746773527283L));

            assertFalse(token.isSuperUser());
        }

        @Test
        void testIsSuperUser_Null() {
            DecodedToken token = new DecodedToken();
            token.setCvManagerData(null);

            assertFalse(token.isSuperUser());
        }
    }

    @Nested
    @DisplayName("Tests for validateExpiration method")
    class ValidateExpirationTests {

        @Test
        @DisplayName("Should accept valid non-expired token")
        void testValidateExpiration_ValidToken() {
            // Create a token that expires in 1 hour
            long futureExpiration = Instant.now().getEpochSecond() + 3600;
            DecodedToken token = new DecodedToken();
            token.setExp(futureExpiration);

            // Should not throw exception
            assertDoesNotThrow(() -> DecodedToken.fromJwtToken(createMockToken(futureExpiration), true));
        }

        @Test
        @DisplayName("Should reject expired token")
        void testValidateExpiration_ExpiredToken() {
            // Create a token that expired 1 hour ago
            long pastExpiration = Instant.now().getEpochSecond() - 3600;
            String expiredToken = createMockToken(pastExpiration);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DecodedToken.fromJwtToken(expiredToken, true));

            assertTrue(exception.getMessage().contains("Token has expired"));
            assertTrue(exception.getMessage().contains("3600 seconds ago") ||
                    exception.getMessage().contains("360") && exception.getMessage().contains("seconds ago"));
        }

        @Test
        @DisplayName("Should reject token expired exactly now")
        void testValidateExpiration_ExpiredExactly() {
            // Create a token that expires at current second
            long currentTime = Instant.now().getEpochSecond();
            String expiredToken = createMockToken(currentTime);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DecodedToken.fromJwtToken(expiredToken, true));

            assertTrue(exception.getMessage().contains("Token has expired"));
        }

        @Test
        @DisplayName("Should reject token expired 1 second ago")
        void testValidateExpiration_ExpiredByOneSecond() {
            // Create a token that expired 1 second ago
            long expiration = Instant.now().getEpochSecond() - 1;
            String expiredToken = createMockToken(expiration);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DecodedToken.fromJwtToken(expiredToken, true));

            assertTrue(exception.getMessage().contains("Token has expired"));
            assertTrue(exception.getMessage().contains("1 second") ||
                    exception.getMessage().contains("seconds ago"));
        }

        @Test
        @DisplayName("Should accept token expiring in 1 second")
        void testValidateExpiration_ExpiringInOneSecond() {
            // Create a token that expires in 1 second
            long futureExpiration = Instant.now().getEpochSecond() + 1;
            String validToken = createMockToken(futureExpiration);

            // Should not throw exception
            assertDoesNotThrow(() -> DecodedToken.fromJwtToken(validToken, true));
        }

        @Test
        @DisplayName("Should handle token without expiration claim")
        void testValidateExpiration_NoExpirationClaim() {
            // Create a token without exp claim (null)
            String tokenWithoutExp = createMockTokenWithoutExp();

            // Should not throw exception - tokens without exp don't expire
            assertDoesNotThrow(() -> DecodedToken.fromJwtToken(tokenWithoutExp, true));
        }

        @Test
        @DisplayName("Should skip validation when checkExpiration is false")
        void testValidateExpiration_SkipCheck() {
            // Create an expired token
            long pastExpiration = Instant.now().getEpochSecond() - 3600;
            String expiredToken = createMockToken(pastExpiration);

            // Should not throw exception when validation is disabled
            assertDoesNotThrow(() -> DecodedToken.fromJwtToken(expiredToken, false));

            DecodedToken token = DecodedToken.fromJwtToken(expiredToken, false);
            assertNotNull(token);
            assertEquals(pastExpiration, token.getExp());
        }

        @Test
        @DisplayName("Should provide detailed error message with timestamps")
        void testValidateExpiration_ErrorMessageFormat() {
            long expiration = Instant.now().getEpochSecond() - 100;
            String expiredToken = createMockToken(expiration);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DecodedToken.fromJwtToken(expiredToken, true));

            String message = exception.getMessage();
            assertTrue(message.contains("Token has expired"));
            assertTrue(message.contains("seconds ago"));
            assertTrue(message.contains("exp:"));
            assertTrue(message.contains("now:"));
        }

        @Test
        @DisplayName("Should accept token with far future expiration")
        void testValidateExpiration_FarFutureExpiration() {
            // Create a token that expires in 10 years
            long farFutureExpiration = Instant.now().getEpochSecond() + (10L * 365 * 24 * 60 * 60);
            String validToken = createMockToken(farFutureExpiration);

            assertDoesNotThrow(() -> DecodedToken.fromJwtToken(validToken, true));
        }

        @Test
        @DisplayName("Should reject token with very old expiration")
        void testValidateExpiration_VeryOldExpiration() {
            // Create a token that expired 10 years ago
            long veryOldExpiration = Instant.now().getEpochSecond() - (10L * 365 * 24 * 60 * 60);
            String expiredToken = createMockToken(veryOldExpiration);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> DecodedToken.fromJwtToken(expiredToken, true));

            assertTrue(exception.getMessage().contains("Token has expired"));
        }

        // Helper methods to create mock tokens

        /**
         * Creates a minimal valid JWT token with the specified expiration time
         */
        private String createMockToken(long expirationTime) {
            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

            String payload = String.format(
                    "{\"exp\":%d,\"iat\":%d,\"sub\":\"test-user\",\"email\":\"test@example.com\",\"cvmanager_data\":{\"super_user\":\"0\",\"organizations\":[]}}",
                    expirationTime,
                    Instant.now().getEpochSecond());

            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("mock-signature".getBytes(StandardCharsets.UTF_8));

            return header + "." + encodedPayload + "." + signature;
        }

        /**
         * Creates a minimal valid JWT token without an expiration claim
         */
        private String createMockTokenWithoutExp() {
            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

            String payload = String.format(
                    "{\"iat\":%d,\"sub\":\"test-user\",\"email\":\"test@example.com\",\"cvmanager_data\":{\"super_user\":\"0\",\"organizations\":[]}}",
                    Instant.now().getEpochSecond());

            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("mock-signature".getBytes(StandardCharsets.UTF_8));

            return header + "." + encodedPayload + "." + signature;
        }
    }
}