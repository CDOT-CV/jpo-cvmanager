package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import us.dot.its.jpo.ode.api.models.keycloak.CvManagerAuthToken;
import us.dot.its.jpo.ode.api.repositories.IntersectionRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.repositories.UserRepository;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private IntersectionRepository intersectionRepository;

    @Mock
    private RsuRepository rsuRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private CvManagerAuthToken authToken;

    @Spy
    @InjectMocks
    private PermissionService permissionService;

    private String tokenString = "mock-token";

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private JwtAuthenticationToken createAuthenticatedToken(String email) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("preferred_username", email)
                .build();
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt);
        token.setAuthenticated(true);
        return token;
    }

    private void setupSecurityContext(JwtAuthenticationToken token) {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(token);
    }

    /**
     * Sets up both Authorization and Organization headers
     */
    private void setupRequestWithHeaders(String bearerToken, String organization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + bearerToken);
        if (organization != null) {
            request.addHeader("Organization", organization);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    // ==================== getAllowedIntersectionIds Tests ====================

    @Test
    void testGetAllowedIntersectionIdsByEmail() {
        when(intersectionRepository.findAllowedIntersectionIdsByEmail("test@example.com"))
                .thenReturn(List.of("123", "456", "789"));

        List<Integer> result = permissionService.getAllowedIntersectionIdsByEmail("test@example.com");

        assertEquals(List.of(123, 456, 789), result);
        verify(intersectionRepository).findAllowedIntersectionIdsByEmail("test@example.com");
    }

    @Test
    void testGetAllowedIntersectionIdsByOrganization() {
        when(intersectionRepository.findIntersectionsByOrganization("TestOrg"))
                .thenReturn(List.of("111", "222"));

        List<Integer> result = permissionService.getAllowedIntersectionIdsByOrganization("TestOrg");

        assertEquals(List.of(111, 222), result);
        verify(intersectionRepository).findIntersectionsByOrganization("TestOrg");
    }

    // ==================== hasRole Tests ====================

    @Test
    void testHasRole_SuperUserAlwaysHasRole() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(true);

        assertTrue(permissionService.hasRole("ADMIN"));
        assertTrue(permissionService.hasRole("OPERATOR"));
        assertTrue(permissionService.hasRole("USER"));
    }

    @Test
    void testHasRole_WithOrganizationHeader_HasSufficientRole() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);
        setupRequestWithHeaders(tokenString, "TestOrg");

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);
        when(authToken.findRoleInOrg("TestOrg")).thenReturn(Optional.of("ADMIN"));

        assertTrue(permissionService.hasRole("OPERATOR"));
    }

    @Test
    void testHasRole_WithOrganizationHeader_InsufficientRole() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);
        setupRequestWithHeaders(tokenString, "TestOrg");

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);
        when(authToken.findRoleInOrg("TestOrg")).thenReturn(Optional.of("USER"));

        assertFalse(permissionService.hasRole("ADMIN"));
    }

    @Test
    void testHasRole_WithoutOrganizationHeader_HasRoleInSomeOrg() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);
        when(authToken.getQualifiedOrgList("OPERATOR")).thenReturn(List.of("TestOrg"));

        assertTrue(permissionService.hasRole("OPERATOR"));
    }

    @Test
    void testHasRole_WithoutOrganizationHeader_NoQualifiedOrgs() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);
        when(authToken.getQualifiedOrgList("ADMIN")).thenReturn(List.of());

        assertFalse(permissionService.hasRole("ADMIN"));
    }

    @Test
    void testHasRole_NotAuthenticated() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        token.setAuthenticated(false);
        setupSecurityContext(token);

        assertFalse(permissionService.hasRole("USER"));
    }

    // ==================== hasRoleInOrg Tests ====================

    @Test
    void testHasRoleInOrg_NotAuthenticated() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        token.setAuthenticated(false);
        setupSecurityContext(token);

        assertFalse(permissionService.hasRoleInOrg("TestOrg", "USER"));
        verify(permissionService, never()).getCvManagerAuthToken();
        verify(authToken, never()).isSuperUser();
    }

    @Test
    void testHasRoleInOrg_SuperUser() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        doReturn(true).when(authToken).isSuperUser();

        assertTrue(permissionService.hasRoleInOrg("TestOrg", "ADMIN"));
        verify(authToken, never()).findRoleInOrg(anyString());
    }

    @Test
    void testHasRoleInOrg_HasExactRole() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        doReturn(Optional.of("OPERATOR")).when(authToken).findRoleInOrg("TestOrg");

        assertTrue(permissionService.hasRoleInOrg("TestOrg", "OPERATOR"));
    }

    @Test
    void testHasRoleInOrg_HasSufficientRole() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        doReturn(Optional.of("ADMIN")).when(authToken).findRoleInOrg("TestOrg");

        assertTrue(permissionService.hasRoleInOrg("TestOrg", "OPERATOR"));
        assertTrue(permissionService.hasRoleInOrg("TestOrg", "USER"));
    }

    @Test
    void testHasRoleInOrg_InsufficientRole() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        doReturn(Optional.of("USER")).when(authToken).findRoleInOrg("TestOrg");

        assertFalse(permissionService.hasRoleInOrg("TestOrg", "OPERATOR"));
        assertFalse(permissionService.hasRoleInOrg("TestOrg", "ADMIN"));
    }

    @Test
    void testHasRoleInOrg_NoRoleInOrganization() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        doReturn(Optional.empty()).when(authToken).findRoleInOrg("TestOrg");

        assertFalse(permissionService.hasRoleInOrg("TestOrg", "USER"));
    }

    // ==================== hasIntersection Tests ====================

    @Test
    void testHasIntersection_NullIntersectionId() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        assertTrue(permissionService.hasIntersection(null, "USER"));
        verify(intersectionRepository, never()).existsByIdAndOrganizations(anyString(), anyList());
    }

    @Test
    void testHasIntersection_NegativeIntersectionId() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        assertTrue(permissionService.hasIntersection(-1, "USER"));
        verify(intersectionRepository, never()).existsByIdAndOrganizations(anyString(), anyList());
    }

    @Test
    void testHasIntersection_SuperUser() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(true);

        assertTrue(permissionService.hasIntersection(123, "USER"));
        verify(intersectionRepository, never()).existsByIdAndOrganizations(anyString(), anyList());
    }

    @Test
    void testHasIntersection_WithOrganizationHeader_HasAccess() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        when(authToken.isSuperUser()).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", "TestOrg");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.getQualifiedOrgList("USER")).thenReturn(List.of("TestOrg"));
        when(intersectionRepository.existsByIdAndOrganizations("123", List.of("TestOrg")))
                .thenReturn(true);

        assertTrue(permissionService.hasIntersection(123, "USER"));
    }

    @Test
    void testHasIntersection_WithOrganizationHeader_NoAccess() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        when(authToken.isSuperUser()).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", "TestOrg");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.getQualifiedOrgList("USER")).thenReturn(List.of("TestOrg"));
        when(intersectionRepository.existsByIdAndOrganizations("123", List.of("TestOrg")))
                .thenReturn(false);

        assertFalse(permissionService.hasIntersection(123, "USER"));
        verify(intersectionRepository).existsByIdAndOrganizations("123", List.of("TestOrg"));
    }

    @Test
    void testHasIntersection_WithoutOrganizationHeader_HasAccessInQualifiedOrg() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);

        when(authToken.getQualifiedOrgList("OPERATOR")).thenReturn(List.of("TestOrg"));
        when(intersectionRepository.existsByIdAndOrganizations("123", List.of("TestOrg")))
                .thenReturn(true);
        assertTrue(permissionService.hasIntersection(123, "OPERATOR"));
    }

    // ==================== hasRsu Tests ====================

    @Test
    void testHasRSU_SuperUser() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(true);

        assertTrue(permissionService.hasRsu("192.168.1.1", "USER"));
        verify(rsuRepository, never()).existsByIpAndOrganizations(any(), anyList());
    }

    @Test
    void testHasRSU_WithOrganizationHeader_HasAccess() throws UnknownHostException {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", "TestOrg");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(authToken.getQualifiedOrgList("USER")).thenReturn(List.of("TestOrg"));
        when(rsuRepository.existsByIpAndOrganizations(InetAddress.getByName("192.168.1.1"), List.of("TestOrg")))
                .thenReturn(true);

        assertTrue(permissionService.hasRsu("192.168.1.1", "USER"));
    }

    @Test
    void testHasRSU_WithOrganizationHeader_NoAccess() throws UnknownHostException {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", "TestOrg");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(authToken.getQualifiedOrgList("USER")).thenReturn(List.of("TestOrg"));
        when(rsuRepository.existsByIpAndOrganizations(InetAddress.getByName("192.168.1.1"), List.of("TestOrg")))
                .thenReturn(false);

        assertFalse(permissionService.hasRsu("192.168.1.1", "USER"));
        verify(rsuRepository).existsByIpAndOrganizations(InetAddress.getByName("192.168.1.1"),
                List.of("TestOrg"));
    }

    @Test
    void testHasRSU_WithoutOrganizationHeader_HasAccessInQualifiedOrg() throws UnknownHostException {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);

        when(authToken.getQualifiedOrgList("OPERATOR")).thenReturn(List.of("TestOrg"));
        when(rsuRepository.existsByIpAndOrganizations(InetAddress.getByName("192.168.1.1"), List.of("TestOrg")))
                .thenReturn(true);

        assertTrue(permissionService.hasRsu("192.168.1.1", "OPERATOR"));
    }

    @Test
    void testHasUser_NotAuthenticated() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        token.setAuthenticated(false);
        setupSecurityContext(token);

        assertFalse(permissionService.hasUser("user@example.com", "USER"));
        verify(userRepository, never()).existsByEmailAndOrganizations(anyString(), anyList());
    }

    @Test
    void testHasUser_SuperUser() {
        JwtAuthenticationToken token = createAuthenticatedToken("admin@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(true);

        assertTrue(permissionService.hasUser("user@example.com", "ADMIN"));
        verify(userRepository, never()).existsByEmailAndOrganizations(anyString(), anyList());
    }

    @Test
    void testHasUser_HasAccessInQualifiedOrganization() {
        JwtAuthenticationToken token = createAuthenticatedToken("manager@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);
        when(authToken.getQualifiedOrgList("ADMIN")).thenReturn(List.of("TestOrg", "AnotherOrg"));
        when(userRepository.existsByEmailAndOrganizations("user@example.com", List.of("TestOrg", "AnotherOrg")))
                .thenReturn(true);

        assertTrue(permissionService.hasUser("user@example.com", "ADMIN"));
        verify(userRepository).existsByEmailAndOrganizations("user@example.com", List.of("TestOrg", "AnotherOrg"));
    }

    @Test
    void testHasUser_NoAccessInQualifiedOrganization() {
        JwtAuthenticationToken token = createAuthenticatedToken("manager@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);
        when(authToken.getQualifiedOrgList("ADMIN")).thenReturn(List.of("TestOrg"));
        when(userRepository.existsByEmailAndOrganizations("user@example.com", List.of("TestOrg")))
                .thenReturn(false);

        assertFalse(permissionService.hasUser("user@example.com", "ADMIN"));
        verify(userRepository).existsByEmailAndOrganizations("user@example.com", List.of("TestOrg"));
    }

    @Test
    void testHasUser_EmptyQualifiedOrganizations() {
        JwtAuthenticationToken token = createAuthenticatedToken("user@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);
        when(authToken.getQualifiedOrgList("ADMIN")).thenReturn(List.of());
        when(userRepository.existsByEmailAndOrganizations("target@example.com", List.of()))
                .thenReturn(false);

        assertFalse(permissionService.hasUser("target@example.com", "ADMIN"));
        verify(userRepository).existsByEmailAndOrganizations("target@example.com", List.of());
    }

    @Test
    void testHasUser_WithOperatorRole() {
        JwtAuthenticationToken token = createAuthenticatedToken("operator@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);
        when(authToken.getQualifiedOrgList("OPERATOR")).thenReturn(List.of("TestOrg"));
        when(userRepository.existsByEmailAndOrganizations("user@example.com", List.of("TestOrg")))
                .thenReturn(true);

        assertTrue(permissionService.hasUser("user@example.com", "OPERATOR"));
    }

    // ==================== hasUsers Tests ====================

    @Test
    void testHasUsers_NotAuthenticated() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        token.setAuthenticated(false);
        setupSecurityContext(token);

        List<String> emails = List.of("user1@example.com", "user2@example.com");

        assertFalse(permissionService.hasUsers(emails, "USER"));
        verify(userRepository, never()).allUsersExistInOrganizations(anyList(), anyList(), anyLong());
    }

    @Test
    void testHasUsers_SuperUser() {
        JwtAuthenticationToken token = createAuthenticatedToken("admin@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(true);

        List<String> emails = List.of("user1@example.com", "user2@example.com", "user3@example.com");

        assertTrue(permissionService.hasUsers(emails, "ADMIN"));
        verify(userRepository, never()).allUsersExistInOrganizations(anyList(), anyList(), anyLong());
    }

    @Test
    void testHasUsers_AllUsersExistInOrganizations() {
        JwtAuthenticationToken token = createAuthenticatedToken("manager@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);

        List<String> emails = List.of("user1@example.com", "user2@example.com", "user3@example.com");
        List<String> qualifiedOrgs = List.of("TestOrg", "AnotherOrg");

        when(authToken.getQualifiedOrgList("ADMIN")).thenReturn(qualifiedOrgs);
        when(userRepository.allUsersExistInOrganizations(emails, qualifiedOrgs, 3L))
                .thenReturn(true);

        assertTrue(permissionService.hasUsers(emails, "ADMIN"));
        verify(userRepository).allUsersExistInOrganizations(emails, qualifiedOrgs, 3L);
    }

    @Test
    void testHasUsers_SomeUsersMissingFromOrganizations() {
        JwtAuthenticationToken token = createAuthenticatedToken("manager@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);

        List<String> emails = List.of("user1@example.com", "user2@example.com", "user3@example.com");
        List<String> qualifiedOrgs = List.of("TestOrg");

        when(authToken.getQualifiedOrgList("OPERATOR")).thenReturn(qualifiedOrgs);
        when(userRepository.allUsersExistInOrganizations(emails, qualifiedOrgs, 3L))
                .thenReturn(false);

        assertFalse(permissionService.hasUsers(emails, "OPERATOR"));
        verify(userRepository).allUsersExistInOrganizations(emails, qualifiedOrgs, 3L);
    }

    @Test
    void testHasUsers_EmptyEmailList() {
        JwtAuthenticationToken token = createAuthenticatedToken("manager@example.com");
        setupSecurityContext(token);

        List<String> emails = List.of();

        assertTrue(permissionService.hasUsers(emails, "USER"));
    }

    @Test
    void testHasUsers_DuplicateEmailsAreHandled() {
        JwtAuthenticationToken token = createAuthenticatedToken("manager@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);

        // Input list with duplicates
        List<String> emails = List.of("user1@example.com", "user2@example.com", "user1@example.com");
        // Expected distinct list
        List<String> distinctEmails = List.of("user1@example.com", "user2@example.com");
        List<String> qualifiedOrgs = List.of("TestOrg");

        when(authToken.getQualifiedOrgList("ADMIN")).thenReturn(qualifiedOrgs);
        when(userRepository.allUsersExistInOrganizations(distinctEmails, qualifiedOrgs, 2L))
                .thenReturn(true);

        assertTrue(permissionService.hasUsers(emails, "ADMIN"));
        verify(userRepository).allUsersExistInOrganizations(distinctEmails, qualifiedOrgs, 2L);
    }

    @Test
    void testHasUsers_SingleUser() {
        JwtAuthenticationToken token = createAuthenticatedToken("manager@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);

        List<String> emails = List.of("user1@example.com");
        List<String> qualifiedOrgs = List.of("TestOrg");

        when(authToken.getQualifiedOrgList("USER")).thenReturn(qualifiedOrgs);
        when(userRepository.allUsersExistInOrganizations(emails, qualifiedOrgs, 1L))
                .thenReturn(true);

        assertTrue(permissionService.hasUsers(emails, "USER"));
        verify(userRepository).allUsersExistInOrganizations(emails, qualifiedOrgs, 1L);
    }

    @Test
    void testHasUsers_NoQualifiedOrganizations() {
        JwtAuthenticationToken token = createAuthenticatedToken("user@example.com");
        setupSecurityContext(token);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);

        List<String> emails = List.of("user1@example.com", "user2@example.com");
        List<String> qualifiedOrgs = List.of();

        when(authToken.getQualifiedOrgList("ADMIN")).thenReturn(qualifiedOrgs);
        when(userRepository.allUsersExistInOrganizations(emails, qualifiedOrgs, 2L))
                .thenReturn(false);

        assertFalse(permissionService.hasUsers(emails, "ADMIN"));
        verify(userRepository).allUsersExistInOrganizations(emails, qualifiedOrgs, 2L);
    }

    // ==================== isAuthValid Tests ====================

    @Test
    void testIsAuthValid_ValidJwtAuthentication() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");

        assertTrue(permissionService.isAuthValid(token));
    }

    @Test
    void testIsAuthValid_NotAuthenticated() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        token.setAuthenticated(false);

        assertFalse(permissionService.isAuthValid(token));
    }

    @Test
    void testIsAuthValid_NotJwtAuthentication() {
        Authentication nonJwtAuth = mock(Authentication.class);
        when(nonJwtAuth.isAuthenticated()).thenReturn(true);

        assertFalse(permissionService.isAuthValid(nonJwtAuth));
    }

    // ==================== Static Utility Tests ====================

    @Test
    void testGetUsername() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        String username = PermissionService.getUsername(token);

        assertEquals("test@example.com", username);
    }

    @Test
    void testGetOrganizationFromHeader_WithHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", "TestOrg");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String organization = PermissionService.getOrganizationFromHeader();

        assertEquals("TestOrg", organization);
    }

    @Test
    void testGetOrganizationFromHeader_WithoutHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String organization = PermissionService.getOrganizationFromHeader();

        assertNull(organization);
    }

    @Test
    void testGetOrganizationFromHeader_NoRequestContext() {
        RequestContextHolder.resetRequestAttributes();

        String organization = PermissionService.getOrganizationFromHeader();

        assertNull(organization);
    }
}