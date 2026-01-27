package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import us.dot.its.jpo.ode.api.models.postgres.tables.User;
import us.dot.its.jpo.ode.api.repositories.IntersectionRepository;
import us.dot.its.jpo.ode.api.repositories.RoleRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.repositories.UserRepository;
import us.dot.its.jpo.ode.api.repositories.UserRepository.UserOrgRoleProjection;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private IntersectionRepository intersectionRepository;

    @Mock
    private RsuRepository rsuRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private PermissionService permissionService;

    private JwtAuthenticationToken jwtAuthenticationToken;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);

        // Create a mock JWT token
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("preferred_username", "test@example.com")
                .build();

        jwtAuthenticationToken = new JwtAuthenticationToken(jwt);

        when(securityContext.getAuthentication()).thenReturn(jwtAuthenticationToken);
    }

    @Test
    void testCheckRoleAbove_AdminAboveOperator() {
        assertTrue(PermissionService.checkRoleAbove("ADMIN", "OPERATOR"));
    }

    @Test
    void testCheckRoleAbove_AdminAboveUser() {
        assertTrue(PermissionService.checkRoleAbove("ADMIN", "USER"));
    }

    @Test
    void testCheckRoleAbove_OperatorAboveUser() {
        assertTrue(PermissionService.checkRoleAbove("OPERATOR", "USER"));
    }

    @Test
    void testCheckRoleAbove_SameRole() {
        assertTrue(PermissionService.checkRoleAbove("ADMIN", "ADMIN"));
        assertTrue(PermissionService.checkRoleAbove("OPERATOR", "OPERATOR"));
        assertTrue(PermissionService.checkRoleAbove("USER", "USER"));
    }

    @Test
    void testCheckRoleAbove_UserNotAboveOperator() {
        assertFalse(PermissionService.checkRoleAbove("USER", "OPERATOR"));
    }

    @Test
    void testCheckRoleAbove_OperatorNotAboveAdmin() {
        assertFalse(PermissionService.checkRoleAbove("OPERATOR", "ADMIN"));
    }

    @Test
    void testCheckRoleAbove_NullRole() {
        assertFalse(PermissionService.checkRoleAbove(null, "ADMIN"));
    }

    @Test
    void testCheckRoleAbove_CaseInsensitive() {
        assertTrue(PermissionService.checkRoleAbove("admin", "user"));
        assertTrue(PermissionService.checkRoleAbove("Admin", "User"));
    }

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

    @Test
    void testIsSuperUser_WhenUserIsSuperUser() {
        User superUser = new User();
        superUser.setSuperUser(true);

        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(superUser));

        assertTrue(permissionService.isSuperUser());
    }

    @Test
    void testIsSuperUser_WhenUserIsNotSuperUser() {
        User regularUser = new User();
        regularUser.setSuperUser(false);

        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(regularUser));

        assertFalse(permissionService.isSuperUser());
    }

    @Test
    void testIsSuperUser_WhenUserNotFound() {
        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        assertFalse(permissionService.isSuperUser());
    }

    @Test
    void testIsSuperUser_WhenNotAuthenticated() {
        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(false);

        assertFalse(permissionService.isSuperUser());
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void testHasRole_SuperUserAlwaysHasRole() {
        User superUser = new User();
        superUser.setSuperUser(true);

        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(superUser));

        assertTrue(permissionService.hasRole("ADMIN"));
        assertTrue(permissionService.hasRole("OPERATOR"));
        assertTrue(permissionService.hasRole("USER"));
    }

    @Test
    void testHasRole_WithOrganizationHeader_HasSufficientRole() {
        User regularUser = new User();
        regularUser.setSuperUser(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", "TestOrg");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(regularUser));
        when(roleRepository.findUserRoleInOrg("test@example.com", "TestOrg"))
                .thenReturn(Optional.of("ADMIN"));

        assertTrue(permissionService.hasRole("OPERATOR"));

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testHasRole_WithOrganizationHeader_InsufficientRole() {
        User regularUser = new User();
        regularUser.setSuperUser(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", "TestOrg");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(regularUser));
        when(roleRepository.findUserRoleInOrg("test@example.com", "TestOrg"))
                .thenReturn(Optional.of("USER"));

        assertFalse(permissionService.hasRole("ADMIN"));

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testHasRole_WithoutOrganizationHeader_HasRoleInSomeOrg() {
        User regularUser = new User();
        regularUser.setSuperUser(false);

        UserOrgRoleProjection projection = mock(UserOrgRoleProjection.class);
        when(projection.getRoleName()).thenReturn("ADMIN");
        when(projection.getOrganizationName()).thenReturn("Org1");

        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(regularUser));
        when(userRepository.findUserOrgRoles("test@example.com")).thenReturn(List.of(projection));

        assertTrue(permissionService.hasRole("OPERATOR"));
    }

    @Test
    void testHasRole_WithoutOrganizationHeader_NoQualifiedOrgs() {
        User regularUser = new User();
        regularUser.setSuperUser(false);

        UserOrgRoleProjection projection = mock(UserOrgRoleProjection.class);
        when(projection.getRoleName()).thenReturn("USER");
        when(projection.getOrganizationName()).thenReturn("Org1");

        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(regularUser));
        when(userRepository.findUserOrgRoles("test@example.com")).thenReturn(List.of(projection));

        assertFalse(permissionService.hasRole("ADMIN"));
    }

    @Test
    void testHasRole_NotAuthenticated() {
        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(false);

        assertFalse(permissionService.hasRole("USER"));
    }

    @Test
    void testHasIntersection_NullIntersectionId() {
        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);

        assertTrue(permissionService.hasIntersection(null, "USER"));
        verify(intersectionRepository, never()).existsByIdAndOrganizations(anyString(), anyList());
    }

    @Test
    void testHasIntersection_NegativeIntersectionId() {
        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);

        assertTrue(permissionService.hasIntersection(-1, "USER"));
        verify(intersectionRepository, never()).existsByIdAndOrganizations(anyString(), anyList());
    }

    @Test
    void testHasIntersection_SuperUser() {
        User superUser = new User();
        superUser.setSuperUser(true);

        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(superUser));

        assertTrue(permissionService.hasIntersection(123, "USER"));
        verify(intersectionRepository, never()).existsByIdAndOrganizations(anyString(), anyList());
    }

    @Test
    void testHasIntersection_WithOrganizationHeader_HasAccess() {
        User regularUser = new User();
        regularUser.setSuperUser(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", "TestOrg");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(regularUser));
        when(intersectionRepository.existsByIdAndOrganizations("123", List.of("TestOrg")))
                .thenReturn(true);

        assertTrue(permissionService.hasIntersection(123, "USER"));

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testHasIntersection_WithOrganizationHeader_NoAccess() {
        User regularUser = new User();
        regularUser.setSuperUser(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", "TestOrg");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(regularUser));
        when(intersectionRepository.existsByIdAndOrganizations("123", List.of("TestOrg")))
                .thenReturn(false);

        assertFalse(permissionService.hasIntersection(123, "USER"));

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testHasIntersection_WithoutOrganizationHeader_HasAccessInQualifiedOrg() {
        User regularUser = new User();
        regularUser.setSuperUser(false);

        UserOrgRoleProjection projection = mock(UserOrgRoleProjection.class);
        when(projection.getRoleName()).thenReturn("ADMIN");
        when(projection.getOrganizationName()).thenReturn("Org1");

        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(regularUser));
        when(userRepository.findUserOrgRoles("test@example.com")).thenReturn(List.of(projection));
        when(intersectionRepository.existsByIdAndOrganizations("123", List.of("Org1")))
                .thenReturn(true);

        assertTrue(permissionService.hasIntersection(123, "OPERATOR"));
    }

    @Test
    void testHasRSU_SuperUser() {
        User superUser = new User();
        superUser.setSuperUser(true);

        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(superUser));

        assertTrue(permissionService.hasRSU("192.168.1.1", "USER"));
        verify(rsuRepository, never()).existsByIpAndOrganizations(anyString(), anyList());
    }

    @Test
    void testHasRSU_WithOrganizationHeader_HasAccess() {
        User regularUser = new User();
        regularUser.setSuperUser(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", "TestOrg");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(regularUser));
        when(rsuRepository.existsByIpAndOrganizations("192.168.1.1", List.of("TestOrg")))
                .thenReturn(true);

        assertTrue(permissionService.hasRSU("192.168.1.1", "USER"));

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testHasRSU_WithOrganizationHeader_NoAccess() {
        User regularUser = new User();
        regularUser.setSuperUser(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", "TestOrg");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(regularUser));
        when(rsuRepository.existsByIpAndOrganizations("192.168.1.1", List.of("TestOrg")))
                .thenReturn(false);

        assertFalse(permissionService.hasRSU("192.168.1.1", "USER"));

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testHasRSU_WithoutOrganizationHeader_HasAccessInQualifiedOrg() {
        User regularUser = new User();
        regularUser.setSuperUser(false);

        UserOrgRoleProjection projection = mock(UserOrgRoleProjection.class);
        when(projection.getRoleName()).thenReturn("ADMIN");
        when(projection.getOrganizationName()).thenReturn("Org1");

        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(regularUser));
        when(userRepository.findUserOrgRoles("test@example.com")).thenReturn(List.of(projection));
        when(rsuRepository.existsByIpAndOrganizations("192.168.1.1", List.of("Org1")))
                .thenReturn(true);

        assertTrue(permissionService.hasRSU("192.168.1.1", "OPERATOR"));
    }

    @Test
    void testIsAuthValid_ValidJwtAuthentication() {
        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(true);

        assertTrue(permissionService.isAuthValid(jwtAuthenticationToken));
    }

    @Test
    void testIsAuthValid_NotAuthenticated() {
        when(jwtAuthenticationToken.isAuthenticated()).thenReturn(false);

        assertFalse(permissionService.isAuthValid(jwtAuthenticationToken));
    }

    @Test
    void testIsAuthValid_NotJwtAuthentication() {
        Authentication nonJwtAuth = mock(Authentication.class);
        when(nonJwtAuth.isAuthenticated()).thenReturn(true);

        assertFalse(permissionService.isAuthValid(nonJwtAuth));
    }

    @Test
    void testGetUsername() {
        String username = PermissionService.getUsername(jwtAuthenticationToken);

        assertEquals("test@example.com", username);
    }

    @Test
    void testGetOrganizationFromHeader_WithHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Organization", "TestOrg");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String organization = PermissionService.getOrganizationFromHeader();

        assertEquals("TestOrg", organization);

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testGetOrganizationFromHeader_WithoutHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String organization = PermissionService.getOrganizationFromHeader();

        assertNull(organization);

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testGetOrganizationFromHeader_NoRequestContext() {
        RequestContextHolder.resetRequestAttributes();

        String organization = PermissionService.getOrganizationFromHeader();

        assertNull(organization);
    }
}