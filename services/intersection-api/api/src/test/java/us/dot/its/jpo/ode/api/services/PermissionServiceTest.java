package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.keycloak.CvManagerAuthToken;
import us.dot.its.jpo.ode.api.repositories.IntersectionRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

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
    private SecurityContext securityContext;

    @Mock
    private CvManagerAuthToken authToken;

    @Spy
    @InjectMocks
    private PermissionService permissionService;

    private String tokenString = "mock-token";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

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

    /**
     * Sets up both Authorization and Organization headers
     */
    private void setupRequestWithHeaders(String bearerToken, Integer organization) {
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
        when(intersectionRepository.findIntersectionsByOrganization(1))
                .thenReturn(List.of("111", "222"));

        List<Integer> result = permissionService.getAllowedIntersectionIdsByOrganization(1);

        assertEquals(List.of(111, 222), result);
        verify(intersectionRepository).findIntersectionsByOrganization(1);
    }

    // ==================== hasRole Tests ====================

    @Test
    void testHasRole_SuperUserAlwaysHasRole() {
        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(true);

        assertTrue(permissionService.hasRole(UserRole.ADMIN));
        assertTrue(permissionService.hasRole(UserRole.OPERATOR));
        assertTrue(permissionService.hasRole(UserRole.USER));
    }

    @Test
    void testHasRole_WithOrganizationHeader_HasSufficientRole() {
        setupRequestWithHeaders(tokenString, 1);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);
        when(authToken.findRoleInOrg(1)).thenReturn(Optional.of(UserRole.ADMIN));

        assertTrue(permissionService.hasRole(UserRole.OPERATOR));
    }

    @Test
    void testHasRole_WithOrganizationHeader_InsufficientRole() {
        setupRequestWithHeaders(tokenString, 1);

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);
        when(authToken.findRoleInOrg(1)).thenReturn(Optional.of(UserRole.USER));

        assertFalse(permissionService.hasRole(UserRole.ADMIN));
    }

    @Test
    void testHasRole_WithoutOrganizationHeader_HasRoleInSomeOrg() {
        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);
        when(authToken.getQualifiedOrgList(UserRole.OPERATOR)).thenReturn(List.of(1));

        assertTrue(permissionService.hasRole(UserRole.OPERATOR));
    }

    @Test
    void testHasRole_WithoutOrganizationHeader_NoQualifiedOrgs() {
        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of());

        assertFalse(permissionService.hasRole(UserRole.ADMIN));
    }

    @Test
    void testHasRole_NotAuthenticated() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        token.setAuthenticated(false);
        when(securityContext.getAuthentication()).thenReturn(token);

        assertThrows(AccessDeniedException.class, () -> permissionService.hasRole(UserRole.USER));
    }

    // ==================== hasRoleInOrg Tests ====================

    @Test
    void testHasRoleInOrg_NotAuthenticated() {
        JwtAuthenticationToken token = createAuthenticatedToken("test@example.com");
        token.setAuthenticated(false);
        when(securityContext.getAuthentication()).thenReturn(token);

        assertThrows(AccessDeniedException.class, () -> permissionService.hasRole(UserRole.USER));
        verify(authToken, never()).getQualifiedOrgList(any());
    }

    @Test
    void testHasRoleInOrg_SuperUser() {
        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        doReturn(true).when(authToken).isSuperUser();

        assertTrue(permissionService.hasRoleInOrg(1, "ADMIN"));
        verify(authToken, never()).findRoleInOrg(anyInt());
    }

    @Test
    void testHasRoleInOrg_HasExactRole() {
        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        doReturn(Optional.of(UserRole.OPERATOR)).when(authToken).findRoleInOrg(1);

        assertTrue(permissionService.hasRoleInOrg(1, "OPERATOR"));
    }

    @Test
    void testHasRoleInOrg_HasSufficientRole() {
        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        doReturn(Optional.of(UserRole.ADMIN)).when(authToken).findRoleInOrg(1);

        assertTrue(permissionService.hasRoleInOrg(1, "OPERATOR"));
        assertTrue(permissionService.hasRoleInOrg(1, "USER"));
    }

    @Test
    void testHasRoleInOrg_InsufficientRole() {
        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        doReturn(Optional.of(UserRole.USER)).when(authToken).findRoleInOrg(1);

        assertFalse(permissionService.hasRoleInOrg(1, "OPERATOR"));
        assertFalse(permissionService.hasRoleInOrg(1, "ADMIN"));
    }

    @Test
    void testHasRoleInOrg_NoRoleInOrganization() {
        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        doReturn(Optional.empty()).when(authToken).findRoleInOrg(1);

        assertFalse(permissionService.hasRoleInOrg(1, "USER"));
    }

    // ==================== hasIntersection Tests ====================

    @Test
    void testHasIntersection_NullIntersectionId() {
        assertTrue(permissionService.hasIntersection(null, "USER"));
        verify(intersectionRepository, never()).existsByIdAndOrganizations(anyString(), anyList());
    }

    @Test
    void testHasIntersection_NegativeIntersectionId() {
        assertTrue(permissionService.hasIntersection(-1, "USER"));
        verify(intersectionRepository, never()).existsByIdAndOrganizations(anyString(), anyList());
    }

    @Test
    void testHasIntersection_SuperUser() {
        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(true);

        assertTrue(permissionService.hasIntersection(123, "USER"));
        verify(intersectionRepository, never()).existsByIdAndOrganizations(anyString(), anyList());
    }

    @Test
    void testHasIntersection_WithOrganizationHeader_HasAccess() {
        when(authToken.isSuperUser()).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", 1);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.getQualifiedOrgList(UserRole.USER)).thenReturn(List.of(1));
        when(intersectionRepository.existsByIdAndOrganizations("123", List.of(1)))
                .thenReturn(true);

        assertTrue(permissionService.hasIntersection(123, "USER"));
    }

    @Test
    void testHasIntersection_WithOrganizationHeader_NoAccess() {
        when(authToken.isSuperUser()).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", 1);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.getQualifiedOrgList(UserRole.USER)).thenReturn(List.of(1));
        when(intersectionRepository.existsByIdAndOrganizations("123", List.of(1)))
                .thenReturn(false);

        assertFalse(permissionService.hasIntersection(123, "USER"));
        verify(intersectionRepository).existsByIdAndOrganizations("123", List.of(1));
    }

    @Test
    void testHasIntersection_WithoutOrganizationHeader_HasAccessInQualifiedOrg() {
        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);

        when(authToken.getQualifiedOrgList(UserRole.OPERATOR)).thenReturn(List.of(1));
        when(intersectionRepository.existsByIdAndOrganizations("123", List.of(1)))
                .thenReturn(true);
        assertTrue(permissionService.hasIntersection(123, "OPERATOR"));
    }

    // ==================== hasRsu Tests ====================

    @Test
    void testHasRSU_SuperUser() {
        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(true);

        assertTrue(permissionService.hasRsu("192.168.1.1", "USER"));
        verify(rsuRepository, never()).existsByIpAndOrganizations(any(), anyList());
    }

    @Test
    void testHasRSU_WithOrganizationHeader_HasAccess() throws UnknownHostException {
        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", 1);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(authToken.getQualifiedOrgList(UserRole.USER)).thenReturn(List.of(1));
        when(rsuRepository.existsByIpAndOrganizations(InetAddress.getByName("192.168.1.1"), List.of(1)))
                .thenReturn(true);

        assertTrue(permissionService.hasRsu("192.168.1.1", "USER"));
    }

    @Test
    void testHasRSU_WithOrganizationHeader_NoAccess() throws UnknownHostException {
        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", 1);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(authToken.getQualifiedOrgList(UserRole.USER)).thenReturn(List.of(1));
        when(rsuRepository.existsByIpAndOrganizations(InetAddress.getByName("192.168.1.1"), List.of(1)))
                .thenReturn(false);

        assertFalse(permissionService.hasRsu("192.168.1.1", "USER"));
        verify(rsuRepository).existsByIpAndOrganizations(InetAddress.getByName("192.168.1.1"),
                List.of(1));
    }

    @Test
    void testHasRSU_WithoutOrganizationHeader_HasAccessInQualifiedOrg() throws UnknownHostException {
        doReturn(authToken).when(permissionService).getCvManagerAuthToken();
        when(authToken.isSuperUser()).thenReturn(false);

        when(authToken.getQualifiedOrgList(UserRole.OPERATOR)).thenReturn(List.of(1));
        when(rsuRepository.existsByIpAndOrganizations(InetAddress.getByName("192.168.1.1"), List.of(1)))
                .thenReturn(true);

        assertTrue(permissionService.hasRsu("192.168.1.1", "OPERATOR"));
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
        request.addHeader("Organization", 1);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Integer organization = PermissionService.getOrganizationFromHeader();

        assertEquals(1, organization);
    }

    @Test
    void testGetOrganizationFromHeader_WithoutHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Integer organization = PermissionService.getOrganizationFromHeader();

        assertNull(organization);
    }

    @Test
    void testGetOrganizationFromHeader_NoRequestContext() {
        RequestContextHolder.resetRequestAttributes();

        Integer organization = PermissionService.getOrganizationFromHeader();

        assertNull(organization);
    }
}