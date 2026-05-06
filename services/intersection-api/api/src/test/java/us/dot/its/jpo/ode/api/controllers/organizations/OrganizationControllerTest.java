package us.dot.its.jpo.ode.api.controllers.organizations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.mappers.OrganizationMapper;
import us.dot.its.jpo.ode.api.mappers.RsuInfoMapper;
import us.dot.its.jpo.ode.api.mappers.UserMapper;
import us.dot.its.jpo.ode.api.models.SimplePosition;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.admin.organization.OrganizationPatch;
import us.dot.its.jpo.ode.api.models.devices.RsuInfoDto;
import us.dot.its.jpo.ode.api.models.keycloak.CvManagerAuthToken;
import us.dot.its.jpo.ode.api.models.organizations.OrganizationDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.User;
import us.dot.its.jpo.ode.api.models.users.UserDto;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.repositories.UserOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.UserRepository;
import us.dot.its.jpo.ode.api.services.OrganizationManagementService;
import us.dot.its.jpo.ode.api.services.PermissionService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrganizationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    PermissionService permissionService;

    @MockitoBean
    OrganizationManagementService organizationManagementService;

    @MockitoBean
    OrganizationMapper organizationMapper;

    @MockitoBean
    OrganizationRepository organizationRepository;

    @MockitoBean
    RsuInfoMapper rsuInfoMapper;

    @MockitoBean
    RsuRepository rsuRepository;

    @MockitoBean
    UserMapper userMapper;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    RsuOrganizationRepository rsuOrganizationRepository;

    @MockitoBean
    UserOrganizationRepository userOrganizationRepository;

    private CvManagerAuthToken authToken;
    private OrganizationPatch validPatch;
    private OrganizationDto sampleOrgDto;
    private RsuInfoDto sampleRsuInfoDto;
    private UserDto sampleUserDto;

    @BeforeEach
    void setUp() {
        authToken = Mockito.mock(CvManagerAuthToken.class);

        validPatch = new OrganizationPatch(
                "TestOrg", "TestOrg", "contact@test.org",
                List.of(), List.of(), List.of(),
                List.of(), List.of(),
                List.of(), List.of(),
                null, null);

        sampleOrgDto = new OrganizationDto("TestOrg", "contact@test.org");

        sampleRsuInfoDto = new RsuInfoDto(
                "192.168.1.1",
                new SimplePosition(39.7392, -104.9903),
                1.5, "I-25", "SN001", "SCMS001",
                null, null, null, null,
                List.of("TestOrg"), false, false);

        sampleUserDto = new UserDto("user@example.com", "Test", "User", false, List.of());
    }

    // ==================== PATCH /organizations ====================

    @Nested
    @DisplayName("PATCH /organizations â€” modifyOrganization")
    class ModifyOrganization {

        @Test
        @DisplayName("returns 403 when no permissions are granted (unauthenticated)")
        void noPermissions_returns403() throws Exception {
            mockMvc.perform(patch("/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validPatch)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 403 when authenticated but isSuperUser and hasRoleInOrg both return false")
        void authenticated_insufficientPermissions_returns403() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(false);
            when(permissionService.hasRoleInOrg(eq("TestOrg"), eq("ADMIN"))).thenReturn(false);

            mockMvc.perform(patch("/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validPatch)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with OrganizationDto when isSuperUser returns true")
        void superUser_returns200WithOrgDto() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(true);
            when(organizationManagementService.modifyOrganization(any(), any())).thenReturn(sampleOrgDto);

            mockMvc.perform(patch("/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validPatch)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("TestOrg"))
                    .andExpect(jsonPath("$.email").value("contact@test.org"));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 when hasRoleInOrg returns true (non-superuser path)")
        void adminInOrg_returns200() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(false);
            when(permissionService.hasRoleInOrg(eq("TestOrg"), eq("ADMIN"))).thenReturn(true);
            when(organizationManagementService.modifyOrganization(any(), any())).thenReturn(sampleOrgDto);

            mockMvc.perform(patch("/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validPatch)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("TestOrg"));
        }

        @Test
        @DisplayName("returns 400 when orig_name is absent from request body")
        void missingOrigName_returns400() throws Exception {
            String bodyMissingOrigName = """
                    {
                      "name": "TestOrg",
                      "users_to_add": [],
                      "users_to_modify": [],
                      "users_to_remove": [],
                      "rsus_to_add": [],
                      "rsus_to_remove": [],
                      "intersections_to_add": [],
                      "intersections_to_remove": []
                    }
                    """;

            mockMvc.perform(patch("/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bodyMissingOrigName))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when name is absent from request body")
        void missingName_returns400() throws Exception {
            String bodyMissingName = """
                    {
                      "orig_name": "TestOrg",
                      "users_to_add": [],
                      "users_to_modify": [],
                      "users_to_remove": [],
                      "rsus_to_add": [],
                      "rsus_to_remove": [],
                      "intersections_to_add": [],
                      "intersections_to_remove": []
                    }
                    """;

            mockMvc.perform(patch("/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bodyMissingName))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when users_to_add is explicitly null")
        void nullUsersToAdd_returns400() throws Exception {
            String bodyNullUsersToAdd = """
                    {
                      "orig_name": "TestOrg",
                      "name": "TestOrg",
                      "users_to_add": null,
                      "users_to_modify": [],
                      "users_to_remove": [],
                      "rsus_to_add": [],
                      "rsus_to_remove": [],
                      "intersections_to_add": [],
                      "intersections_to_remove": []
                    }
                    """;

            mockMvc.perform(patch("/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bodyNullUsersToAdd))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 404 when service throws ResponseStatusException with NOT_FOUND")
        void serviceThrowsNotFound_returns404() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(true);
            doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found: TestOrg"))
                    .when(organizationManagementService).modifyOrganization(any(), any());

            mockMvc.perform(patch("/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validPatch)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser
        @DisplayName("delegates patch payload and auth token to organizationManagementService")
        void superUser_delegatesToService() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(true);
            when(organizationManagementService.modifyOrganization(any(), any())).thenReturn(sampleOrgDto);

            mockMvc.perform(patch("/organizations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validPatch)))
                    .andExpect(status().isOk());

            verify(organizationManagementService).modifyOrganization(any(), any());
        }
    }

    // ==================== GET /organizations ====================

    @Nested
    @DisplayName("GET /organizations â€” getOrganizations")
    class GetOrganizations {

        @Test
        @DisplayName("returns 403 when no permissions are granted (unauthenticated)")
        void noPermissions_returns403() throws Exception {
            mockMvc.perform(get("/organizations"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 403 when authenticated but neither isSuperUser nor hasRole('ADMIN')")
        void authenticated_insufficientPermissions_returns403() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(false);
            when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(false);

            mockMvc.perform(get("/organizations"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with all organizations when isSuperUser returns true")
        void superUser_returns200WithAllOrgs() throws Exception {
            Organization mockOrg = Mockito.mock(Organization.class);
            when(permissionService.isSuperUser()).thenReturn(true);
            when(organizationRepository.findAll()).thenReturn(List.of(mockOrg));
            when(organizationMapper.toDto(mockOrg)).thenReturn(sampleOrgDto);

            mockMvc.perform(get("/organizations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].name").value("TestOrg"))
                    .andExpect(jsonPath("$[0].email").value("contact@test.org"));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with qualified organizations when hasRole('ADMIN') returns true")
        void adminRole_returns200WithQualifiedOrgs() throws Exception {
            Organization mockOrg = Mockito.mock(Organization.class);
            when(permissionService.isSuperUser()).thenReturn(false);
            when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
            when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
            when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of("TestOrg"));
            when(organizationRepository.findByNameIn(List.of("TestOrg"))).thenReturn(List.of(mockOrg));
            when(organizationMapper.toDto(mockOrg)).thenReturn(sampleOrgDto);

            mockMvc.perform(get("/organizations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].name").value("TestOrg"));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with empty list when no organizations are found")
        void superUser_noOrgs_returns200WithEmptyList() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(true);
            when(organizationRepository.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/organizations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    // ==================== GET /organizations/rsus ====================

    @Nested
    @DisplayName("GET /organizations/rsus â€” getRsuIpsByOrganization")
    class GetRsuIpsByOrganization {

        @Test
        @DisplayName("returns 403 when no permissions are granted (unauthenticated)")
        void noPermissions_returns403() throws Exception {
            mockMvc.perform(get("/organizations/rsus")
                    .header("Organization", "TestOrg"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 400 when Organization header is missing")
        void missingOrganizationHeader_returns400() throws Exception {
            mockMvc.perform(get("/organizations/rsus"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 403 when authenticated but neither isSuperUser nor hasRole('ADMIN')")
        void authenticated_insufficientPermissions_returns403() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(false);
            when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(false);

            mockMvc.perform(get("/organizations/rsus")
                    .header("Organization", "TestOrg"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with list of IP strings when isSuperUser returns true")
        void superUser_returns200WithRsuIps() throws Exception {
            java.net.InetAddress ip1 = java.net.InetAddress.getByName("192.168.1.1");
            java.net.InetAddress ip2 = java.net.InetAddress.getByName("10.0.0.1");
            when(permissionService.isSuperUser()).thenReturn(true);
            when(rsuOrganizationRepository.findAllRsuIpsByOrganizationName("TestOrg"))
                    .thenReturn(List.of(ip1, ip2));

            mockMvc.perform(get("/organizations/rsus")
                    .header("Organization", "TestOrg"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0]").value("192.168.1.1"))
                    .andExpect(jsonPath("$[1]").value("10.0.0.1"));
        }

        @Test
        @WithMockUser
        @DisplayName("passes the Organization header value to the repository")
        void organizationHeader_isForwardedToRepository() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(true);
            when(rsuOrganizationRepository.findAllRsuIpsByOrganizationName("TestOrg"))
                    .thenReturn(List.of());

            mockMvc.perform(get("/organizations/rsus")
                    .header("Organization", "TestOrg"))
                    .andExpect(status().isOk());

            verify(rsuOrganizationRepository).findAllRsuIpsByOrganizationName("TestOrg");
        }
    }

    // ==================== GET /organizations/rsus/{rsuIp} ====================

    @Nested
    @DisplayName("GET /organizations/rsus/{rsuIp} â€” getRsuOrganizationAssignments")
    class GetRsuOrganizationAssignments {

        @Test
        @DisplayName("returns 403 when no permissions are granted (unauthenticated)")
        void noPermissions_returns403() throws Exception {
            mockMvc.perform(get("/organizations/rsus/192.168.1.1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 403 when authenticated but neither isSuperUser nor hasRsu+hasRole('ADMIN')")
        void authenticated_insufficientPermissions_returns403() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(false);
            when(permissionService.hasRsu(eq("192.168.1.1"), eq("ADMIN"))).thenReturn(false);

            mockMvc.perform(get("/organizations/rsus/192.168.1.1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with list of org names when isSuperUser returns true")
        void superUser_returns200WithOrgNames() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(true);
            when(rsuRepository.findAllOrganizationNamesByIpv4Address(any()))
                    .thenReturn(List.of("TestOrg", "OtherOrg"));

            mockMvc.perform(get("/organizations/rsus/192.168.1.1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0]").value("TestOrg"))
                    .andExpect(jsonPath("$[1]").value("OtherOrg"));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 when hasRsu and hasRole('ADMIN') both return true")
        void adminWithRsuAccess_returns200() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(false);
            when(permissionService.hasRsu(eq("192.168.1.1"), eq("ADMIN"))).thenReturn(true);
            when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
            when(rsuRepository.findAllOrganizationNamesByIpv4Address(any()))
                    .thenReturn(List.of("TestOrg"));

            mockMvc.perform(get("/organizations/rsus/192.168.1.1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("TestOrg"));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 400 when rsuIp is not a valid IP address")
        void invalidRsuIp_returns400() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(true);

            mockMvc.perform(get("/organizations/rsus/not-an-ip"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== GET /organizations/rsus/available ====================

    @Nested
    @DisplayName("GET /organizations/rsus/available â€” getRsuIpsNotInOrganization")
    class GetRsuIpsNotInOrganization {

        @Test
        @DisplayName("returns 403 when no permissions are granted (unauthenticated)")
        void noPermissions_returns403() throws Exception {
            mockMvc.perform(get("/organizations/rsus/available")
                    .header("Organization", "TestOrg"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 400 when Organization header is missing")
        void missingOrganizationHeader_returns400() throws Exception {
            mockMvc.perform(get("/organizations/rsus/available"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 403 when authenticated but neither isSuperUser nor hasRole('ADMIN')")
        void authenticated_insufficientPermissions_returns403() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(false);
            when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(false);

            mockMvc.perform(get("/organizations/rsus/available")
                    .header("Organization", "TestOrg"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with list of available RsuInfoDto objects when isSuperUser returns true")
        void superUser_returns200WithAvailableRsus() throws Exception {
            Rsu mockRsu = Mockito.mock(Rsu.class);
            when(permissionService.isSuperUser()).thenReturn(true);
            when(rsuOrganizationRepository.findAllRsusNotInOrganizationName("TestOrg"))
                    .thenReturn(List.of(mockRsu));
            when(rsuInfoMapper.toDto(mockRsu)).thenReturn(sampleRsuInfoDto);

            mockMvc.perform(get("/organizations/rsus/available")
                    .header("Organization", "TestOrg"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].ip").value("192.168.1.1"));
        }
    }

    // ==================== GET /organizations/users ====================

    @Nested
    @DisplayName("GET /organizations/users â€” getUserEmailsByOrganization")
    class GetUserEmailsByOrganization {

        @Test
        @DisplayName("returns 403 when no permissions are granted (unauthenticated)")
        void noPermissions_returns403() throws Exception {
            mockMvc.perform(get("/organizations/users")
                    .header("Organization", "TestOrg"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 400 when Organization header is missing")
        void missingOrganizationHeader_returns400() throws Exception {
            mockMvc.perform(get("/organizations/users"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 403 when authenticated but neither isSuperUser nor hasRole('ADMIN')")
        void authenticated_insufficientPermissions_returns403() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(false);
            when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(false);

            mockMvc.perform(get("/organizations/users")
                    .header("Organization", "TestOrg"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with list of user email strings when isSuperUser returns true")
        void superUser_returns200WithUserEmails() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(true);
            when(userOrganizationRepository.findAllUserEmailsByOrganizationName("TestOrg"))
                    .thenReturn(List.of("user1@example.com", "user2@example.com"));

            mockMvc.perform(get("/organizations/users")
                    .header("Organization", "TestOrg"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0]").value("user1@example.com"))
                    .andExpect(jsonPath("$[1]").value("user2@example.com"));
        }

        @Test
        @WithMockUser
        @DisplayName("passes the Organization header value to the repository")
        void organizationHeader_isForwardedToRepository() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(true);
            when(userOrganizationRepository.findAllUserEmailsByOrganizationName("TestOrg"))
                    .thenReturn(List.of());

            mockMvc.perform(get("/organizations/users")
                    .header("Organization", "TestOrg"))
                    .andExpect(status().isOk());

            verify(userOrganizationRepository).findAllUserEmailsByOrganizationName("TestOrg");
        }
    }

    // ==================== GET /organizations/users/{email} ====================

    @Nested
    @DisplayName("GET /organizations/users/{email} — getUserOrganizationAssignments")
    class GetUserOrganizationAssignments {

        @Test
        @DisplayName("returns 403 when no permissions are granted (unauthenticated)")
        void noPermissions_returns403() throws Exception {
            mockMvc.perform(get("/organizations/users/user@example.com"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 403 when authenticated but isSuperUser returns false")
        void authenticated_noSuperUser_returns403() throws Exception {
            // @PermissionService.hasUser() does not exist in PermissionService;
            // SpEL evaluation fails when isSuperUser is false -> access denied.
            when(permissionService.isSuperUser()).thenReturn(false);

            mockMvc.perform(get("/organizations/users/user@example.com"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with list of org names when isSuperUser returns true")
        void superUser_returns200WithOrgNames() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(true);
            when(userRepository.findAllOrganizationNamesByEmail("user@example.com"))
                    .thenReturn(List.of("TestOrg", "OtherOrg"));

            mockMvc.perform(get("/organizations/users/user@example.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0]").value("TestOrg"))
                    .andExpect(jsonPath("$[1]").value("OtherOrg"));
        }
    }

    // ==================== GET /organizations/users/available ====================

    @Nested
    @DisplayName("GET /organizations/users/available — getUserEmailsNotInOrganization")
    class GetUserEmailsNotInOrganization {

        @Test
        @DisplayName("returns 403 when no permissions are granted (unauthenticated)")
        void noPermissions_returns403() throws Exception {
            mockMvc.perform(get("/organizations/users/available")
                    .header("Organization", "TestOrg"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 400 when Organization header is missing")
        void missingOrganizationHeader_returns400() throws Exception {
            mockMvc.perform(get("/organizations/users/available"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 403 when authenticated but neither isSuperUser nor hasRole('ADMIN')")
        void authenticated_insufficientPermissions_returns403() throws Exception {
            when(permissionService.isSuperUser()).thenReturn(false);
            when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(false);

            mockMvc.perform(get("/organizations/users/available")
                    .header("Organization", "TestOrg"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with list of available UserDto objects when isSuperUser returns true")
        void superUser_returns200WithAvailableUsers() throws Exception {
            User mockUser = Mockito.mock(User.class);
            when(permissionService.isSuperUser()).thenReturn(true);
            when(userOrganizationRepository.findAllUserEmailsNotInOrganizationName("TestOrg"))
                    .thenReturn(List.of(mockUser));
            when(userMapper.toDto(mockUser)).thenReturn(sampleUserDto);

            mockMvc.perform(get("/organizations/users/available")
                    .header("Organization", "TestOrg"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].email").value("user@example.com"));
        }
    }
}
