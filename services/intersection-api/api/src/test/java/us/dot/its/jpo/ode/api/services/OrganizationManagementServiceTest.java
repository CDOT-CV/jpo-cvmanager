package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.mappers.OrganizationMapper;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.admin.organization.OrganizationPatch;
import us.dot.its.jpo.ode.api.models.admin.organization.UserRoleAssignment;
import us.dot.its.jpo.ode.api.models.keycloak.CvManagerAuthToken;
import us.dot.its.jpo.ode.api.models.organizations.OrganizationDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.Intersection;
import us.dot.its.jpo.ode.api.models.postgres.tables.IntersectionOrganization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Role;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuOption;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuOrganization;
import us.dot.its.jpo.ode.api.models.postgres.tables.User;
import us.dot.its.jpo.ode.api.models.postgres.tables.UserOrganization;
import us.dot.its.jpo.ode.api.repositories.IntersectionOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.IntersectionRepository;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RoleRepository;
import us.dot.its.jpo.ode.api.repositories.RsuOptionRepository;
import us.dot.its.jpo.ode.api.repositories.RsuOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.repositories.UserOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.UserRepository;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.InOrder;

@ExtendWith(MockitoExtension.class)
class OrganizationManagementServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserOrganizationRepository userOrganizationRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RsuRepository rsuRepository;
    @Mock
    private RsuOrganizationRepository rsuOrganizationRepository;
    @Mock
    private RsuOptionRepository rsuOptionRepository;
    @Mock
    private IntersectionRepository intersectionRepository;
    @Mock
    private IntersectionOrganizationRepository intersectionOrganizationRepository;
    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private CvManagerAuthToken authToken;

    @InjectMocks
    private OrganizationManagementService service;

    private Organization testOrg;
    private OrganizationDto testOrgDto;

    @BeforeEach
    void setUp() {
        testOrg = new Organization();
        testOrg.setId(1);
        testOrg.setName("TestOrg");
        testOrg.setEmail("test@org.com");

        testOrgDto = new OrganizationDto(1, "TestOrg", "test@org.com");
    }

    /**
     * Builds a minimal no-op patch: all list fields empty, no RSU option flags.
     * Callers override only the fields relevant to their test.
     */
    private OrganizationPatch minimalPatch() {
        OrganizationPatch patch = new OrganizationPatch();
        patch.setId(1);
        patch.setName("TestOrg");
        patch.setEmail("test@org.com");
        patch.setUsersToAdd(List.of());
        patch.setUsersToModify(List.of());
        patch.setUsersToRemove(List.of());
        patch.setRsusToAdd(List.of());
        patch.setRsusToRemove(List.of());
        patch.setIntersectionsToAdd(List.of());
        patch.setIntersectionsToRemove(List.of());
        return patch;
    }

    /**
     * Stubs the auth + org-load + save + map sequence used by all happy-path tests.
     * Uses isSuperUser=true to skip the inner authorization checks.
     */
    private void stubBaseFlow() {
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(testOrg));
        when(authToken.isSuperUser()).thenReturn(true);
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(organizationRepository.save(testOrg)).thenReturn(testOrg);
        when(organizationMapper.toDto(testOrg)).thenReturn(testOrgDto);
    }

    // =========================================================================
    // modifyOrganization — authorization and basic flow
    // =========================================================================

    @Test
    void testModifyOrganization_SuperUser_CanModifyAnyOrg() {
        OrganizationPatch patch = minimalPatch();
        when(authToken.isSuperUser()).thenReturn(true);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of()); // not in any org
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(organizationRepository.save(testOrg)).thenReturn(testOrg);
        when(organizationMapper.toDto(testOrg)).thenReturn(testOrgDto);

        OrganizationDto result = service.modifyOrganization(patch, authToken);

        assertNotNull(result);
        assertEquals(1, result.getId());
    }

    @Test
    void testModifyOrganization_AdminInOrg_Allowed() {
        OrganizationPatch patch = minimalPatch();
        when(authToken.isSuperUser()).thenReturn(false);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(testOrg));
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(organizationRepository.save(testOrg)).thenReturn(testOrg);
        when(organizationMapper.toDto(testOrg)).thenReturn(testOrgDto);

        OrganizationDto result = service.modifyOrganization(patch, authToken);

        assertNotNull(result);
    }

    @Test
    void testModifyOrganization_NonAdmin_NotFound() {
        OrganizationPatch patch = minimalPatch();
        when(authToken.isSuperUser()).thenReturn(false);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(testOrg));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.modifyOrganization(patch, authToken));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void testModifyOrganization_OrgNotFound() {
        OrganizationPatch patch = minimalPatch();
        when(authToken.isSuperUser()).thenReturn(true);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of());
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.modifyOrganization(patch, authToken));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void testModifyOrganization_UpdatesNameAndEmail() {
        OrganizationPatch patch = minimalPatch();
        patch.setName("RenamedOrg");
        patch.setEmail("new@org.com");
        stubBaseFlow();

        service.modifyOrganization(patch, authToken);

        assertEquals("RenamedOrg", testOrg.getName());
        assertEquals("new@org.com", testOrg.getEmail());
        verify(organizationRepository).save(testOrg);
    }

    @Test
    void testModifyOrganization_ReturnsMapperDto() {
        stubBaseFlow();

        OrganizationDto result = service.modifyOrganization(minimalPatch(), authToken);

        assertSame(testOrgDto, result);
        verify(organizationMapper).toDto(testOrg);
    }

    // =========================================================================
    // applyBulkRsuOptions (exercised via timDeposit / snmpMonitoring in patch)
    // =========================================================================

    @Test
    void testApplyBulkRsuOptions_NeitherFlagSet_StepSkipped() {
        stubBaseFlow();
        // timDeposit and snmpMonitoring both null — step 3 should be skipped entirely

        service.modifyOrganization(minimalPatch(), authToken);

        verifyNoInteractions(rsuOptionRepository);
        verify(rsuOrganizationRepository, never()).findAllRsuIpsByOrganizationId(any());
    }

    @Test
    void testApplyBulkRsuOptions_EmptyRsuList_NoSave() {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setTimDeposit(true);
        when(rsuOrganizationRepository.findAllRsuIpsByOrganizationId(testOrg.getId())).thenReturn(List.of());

        service.modifyOrganization(patch, authToken);

        verify(rsuOptionRepository, never()).saveAll(any());
    }

    @Test
    void testApplyBulkRsuOptions_ExistingOption_UpdatedAndSaved() throws UnknownHostException {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setTimDeposit(true);

        InetAddress ip = InetAddress.getByName("192.168.1.1");
        Rsu rsu = new Rsu();
        rsu.setId(1);
        rsu.setIpv4Address(ip);

        RsuOption existingOption = new RsuOption();
        existingOption.setId(1);
        existingOption.setRsu(rsu);
        existingOption.setTimDeposit(false);
        existingOption.setSnmpMonitoring(false);

        when(rsuOrganizationRepository.findAllRsuIpsByOrganizationId(testOrg.getId())).thenReturn(List.of(ip));
        when(rsuRepository.findByIpv4AddressIn(List.of(ip))).thenReturn(List.of(rsu));
        when(rsuOptionRepository.findAllById(List.of(1))).thenReturn(List.of(existingOption));

        service.modifyOrganization(patch, authToken);

        assertTrue(existingOption.getTimDeposit());
        verify(rsuOptionRepository).saveAll(argThat(list -> ((List<?>) list).size() == 1));
    }

    @Test
    void testApplyBulkRsuOptions_NoExistingOption_NewOptionCreated() throws UnknownHostException {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setSnmpMonitoring(true);

        InetAddress ip = InetAddress.getByName("10.0.0.1");
        Rsu rsu = new Rsu();
        rsu.setId(2);
        rsu.setIpv4Address(ip);

        when(rsuOrganizationRepository.findAllRsuIpsByOrganizationId(testOrg.getId())).thenReturn(List.of(ip));
        when(rsuRepository.findByIpv4AddressIn(List.of(ip))).thenReturn(List.of(rsu));
        when(rsuOptionRepository.findAllById(List.of(2))).thenReturn(List.of()); // no pre-existing option

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RsuOption>> captor = ArgumentCaptor.forClass(List.class);
        service.modifyOrganization(patch, authToken);

        verify(rsuOptionRepository).saveAll(captor.capture());
        List<RsuOption> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertSame(rsu, saved.get(0).getRsu());
        assertTrue(saved.get(0).getSnmpMonitoring());
    }

    @Test
    void testApplyBulkRsuOptions_BothFlags_BothFieldsUpdated() throws UnknownHostException {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setTimDeposit(true);
        patch.setSnmpMonitoring(false);

        InetAddress ip = InetAddress.getByName("172.16.0.1");
        Rsu rsu = new Rsu();
        rsu.setId(3);
        rsu.setIpv4Address(ip);

        RsuOption option = new RsuOption();
        option.setId(3);
        option.setRsu(rsu);

        when(rsuOrganizationRepository.findAllRsuIpsByOrganizationId(testOrg.getId())).thenReturn(List.of(ip));
        when(rsuRepository.findByIpv4AddressIn(List.of(ip))).thenReturn(List.of(rsu));
        when(rsuOptionRepository.findAllById(List.of(3))).thenReturn(List.of(option));

        service.modifyOrganization(patch, authToken);

        assertTrue(option.getTimDeposit());
        assertFalse(option.getSnmpMonitoring());
    }

    // =========================================================================
    // handleUsersToAdd
    // =========================================================================

    @Test
    void testHandleUsersToAdd_EmptyList_NoOps() {
        stubBaseFlow();

        service.modifyOrganization(minimalPatch(), authToken);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(roleRepository);
        verify(userOrganizationRepository, never()).saveAll(any());
    }

    @Test
    void testHandleUsersToAdd_NonSuperUser_NewOrgNameNotFound() {
        // Rename scenario: caller is ADMIN of origName but not of the new name.
        OrganizationPatch patch = minimalPatch();
        patch.setId(1);
        patch.setName("TestOrgNew"); // renamed; caller is not ADMIN of the new name
        patch.setUsersToAdd(List.of(new UserRoleAssignment("user@test.com", "operator")));

        when(authToken.isSuperUser()).thenReturn(false);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(testOrg));
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(organizationRepository.save(testOrg)).thenReturn(testOrg);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.modifyOrganization(patch, authToken));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(userOrganizationRepository, never()).saveAll(any());
    }

    @Test
    void testHandleUsersToAdd_AlreadyMember_Skipped() {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setUsersToAdd(List.of(new UserRoleAssignment("existing@test.com", "operator")));

        when(userOrganizationRepository.findByUser_EmailAndOrganization("existing@test.com", testOrg))
                .thenReturn(Optional.of(new UserOrganization()));

        service.modifyOrganization(patch, authToken);

        verifyNoInteractions(userRepository); // user entity never loaded
        verifyNoInteractions(roleRepository); // role entity never loaded
        verify(userOrganizationRepository).saveAll(argThat(list -> ((List<?>) list).isEmpty()));
    }

    @Test
    void testHandleUsersToAdd_UserNotFound_NotFound() {
        OrganizationPatch patch = minimalPatch();
        patch.setUsersToAdd(List.of(new UserRoleAssignment("ghost@test.com", "operator")));

        when(authToken.isSuperUser()).thenReturn(true);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(testOrg));
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(organizationRepository.save(testOrg)).thenReturn(testOrg);
        when(userOrganizationRepository.findByUser_EmailAndOrganization("ghost@test.com", testOrg))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.modifyOrganization(patch, authToken));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("User not found"));
    }

    @Test
    void testHandleUsersToAdd_RoleNotFound_BadRequest() {
        OrganizationPatch patch = minimalPatch();
        patch.setUsersToAdd(List.of(new UserRoleAssignment("user@test.com", "unknown_role")));

        User user = new User();
        user.setId(1);
        user.setEmail("user@test.com");

        when(authToken.isSuperUser()).thenReturn(true);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(testOrg));
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(organizationRepository.save(testOrg)).thenReturn(testOrg);
        when(userOrganizationRepository.findByUser_EmailAndOrganization("user@test.com", testOrg))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(roleRepository.findByName("unknown_role")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.modifyOrganization(patch, authToken));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Role not found"));
    }

    @Test
    void testHandleUsersToAdd_Success_SaveAll() {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setUsersToAdd(List.of(new UserRoleAssignment("new@test.com", "operator")));

        User user = new User();
        user.setId(2);
        user.setEmail("new@test.com");

        Role role = new Role();
        role.setId(1);
        role.setName("operator");

        when(userOrganizationRepository.findByUser_EmailAndOrganization("new@test.com", testOrg))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.of(user));
        when(roleRepository.findByName("operator")).thenReturn(Optional.of(role));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserOrganization>> captor = ArgumentCaptor.forClass(List.class);
        service.modifyOrganization(patch, authToken);

        verify(userOrganizationRepository).saveAll(captor.capture());
        List<UserOrganization> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertSame(user, saved.get(0).getUser());
        assertSame(role, saved.get(0).getRole());
        assertSame(testOrg, saved.get(0).getOrganization());
    }

    @Test
    void testHandleUsersToAdd_SameRoleTwice_RoleCacheHit() {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setUsersToAdd(List.of(
                new UserRoleAssignment("user1@test.com", "operator"),
                new UserRoleAssignment("user2@test.com", "operator")));

        User user1 = new User();
        user1.setId(1);
        user1.setEmail("user1@test.com");
        User user2 = new User();
        user2.setId(2);
        user2.setEmail("user2@test.com");
        Role role = new Role();
        role.setId(1);
        role.setName("operator");

        when(userOrganizationRepository.findByUser_EmailAndOrganization(anyString(), eq(testOrg)))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user1@test.com")).thenReturn(Optional.of(user1));
        when(userRepository.findByEmail("user2@test.com")).thenReturn(Optional.of(user2));
        when(roleRepository.findByName("operator")).thenReturn(Optional.of(role));

        service.modifyOrganization(patch, authToken);

        // Role DB lookup must happen only once (cache hit on second user)
        verify(roleRepository, times(1)).findByName("operator");
        verify(userOrganizationRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
    }

    // =========================================================================
    // handleUsersToModify
    // =========================================================================

    @Test
    void testHandleUsersToModify_EmptyList_NoOps() {
        stubBaseFlow();

        service.modifyOrganization(minimalPatch(), authToken);

        // saveAll should not be called for the modify path when the list is empty
        verify(userOrganizationRepository, never()).saveAll(any());
    }

    @Test
    void testHandleUsersToModify_UserNotMember_NotFound() {
        OrganizationPatch patch = minimalPatch();
        patch.setUsersToModify(List.of(new UserRoleAssignment("nonmember@test.com", "admin")));

        when(authToken.isSuperUser()).thenReturn(true);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(testOrg));
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(organizationRepository.save(testOrg)).thenReturn(testOrg);
        when(userOrganizationRepository.findByUser_EmailAndOrganization("nonmember@test.com", testOrg))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.modifyOrganization(patch, authToken));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("not a member"));
    }

    @Test
    void testHandleUsersToModify_RoleNotFound_BadRequest() {
        OrganizationPatch patch = minimalPatch();
        patch.setUsersToModify(List.of(new UserRoleAssignment("member@test.com", "ghost_role")));

        when(authToken.isSuperUser()).thenReturn(true);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(testOrg));
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(organizationRepository.save(testOrg)).thenReturn(testOrg);
        UserOrganization userOrg = new UserOrganization();
        when(userOrganizationRepository.findByUser_EmailAndOrganization("member@test.com", testOrg))
                .thenReturn(Optional.of(userOrg));
        when(roleRepository.findByName("ghost_role")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.modifyOrganization(patch, authToken));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Role not found"));
    }

    @Test
    void testHandleUsersToModify_Success_RoleUpdatedAndSaved() {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setUsersToModify(List.of(new UserRoleAssignment("member@test.com", "admin")));

        UserOrganization userOrg = new UserOrganization();
        Role adminRole = new Role();
        adminRole.setName("admin");

        when(userOrganizationRepository.findByUser_EmailAndOrganization("member@test.com", testOrg))
                .thenReturn(Optional.of(userOrg));
        when(roleRepository.findByName("admin")).thenReturn(Optional.of(adminRole));

        service.modifyOrganization(patch, authToken);

        assertSame(adminRole, userOrg.getRole());
        verify(userOrganizationRepository).saveAll(argThat(list -> ((List<?>) list).size() == 1));
    }

    @Test
    void testHandleUsersToModify_SameRoleTwice_RoleCacheHit() {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setUsersToModify(List.of(
                new UserRoleAssignment("u1@test.com", "operator"),
                new UserRoleAssignment("u2@test.com", "operator")));

        UserOrganization uo1 = new UserOrganization();
        UserOrganization uo2 = new UserOrganization();
        Role role = new Role();
        role.setName("operator");

        when(userOrganizationRepository.findByUser_EmailAndOrganization("u1@test.com", testOrg))
                .thenReturn(Optional.of(uo1));
        when(userOrganizationRepository.findByUser_EmailAndOrganization("u2@test.com", testOrg))
                .thenReturn(Optional.of(uo2));
        when(roleRepository.findByName("operator")).thenReturn(Optional.of(role));

        service.modifyOrganization(patch, authToken);

        verify(roleRepository, times(1)).findByName("operator");
        verify(userOrganizationRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
    }

    // =========================================================================
    // usersToRemove (step 6)
    // =========================================================================

    @Test
    void testUsersToRemove_EmptyList_NoDelete() {
        stubBaseFlow();

        service.modifyOrganization(minimalPatch(), authToken);

        verify(userOrganizationRepository, never()).deleteByUserEmailsAndOrganization(any(), any());
    }

    @Test
    void testUsersToRemove_NonEmpty_BatchDeletes() {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setUsersToRemove(List.of("remove1@test.com", "remove2@test.com"));

        service.modifyOrganization(patch, authToken);

        verify(userOrganizationRepository).deleteByUserEmailsAndOrganization(
                List.of("remove1@test.com", "remove2@test.com"), testOrg);
    }

    // =========================================================================
    // handleRsusToAdd
    // =========================================================================

    @Test
    void testHandleRsusToAdd_EmptyList_NoOps() {
        stubBaseFlow();

        service.modifyOrganization(minimalPatch(), authToken);

        verify(rsuOrganizationRepository, never()).saveAll(any());
    }

    @Test
    void testHandleRsusToAdd_OrgNotFound_NotFound() {
        OrganizationPatch patch = minimalPatch();
        patch.setRsusToAdd(List.of("192.168.1.5"));

        when(authToken.isSuperUser()).thenReturn(true);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of());
        // Step 2 finds the org; handleRsusToAdd's findById call returns empty
        when(organizationRepository.findById(testOrg.getId()))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.modifyOrganization(patch, authToken));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Organization not found"));
        verify(rsuOrganizationRepository, never()).saveAll(any());
    }

    @Test
    void testHandleRsusToAdd_AlreadyAssigned_Skipped() throws UnknownHostException {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setRsusToAdd(List.of("10.0.0.1"));

        InetAddress ip = InetAddress.getByName("10.0.0.1");
        when(rsuOrganizationRepository.findByRsuIpv4AddressAndOrganization(ip, testOrg))
                .thenReturn(Optional.of(new RsuOrganization()));

        service.modifyOrganization(patch, authToken);

        verify(rsuRepository, never()).findByIpv4Address(any()); // RSU entity never loaded
        verify(rsuOrganizationRepository).saveAll(argThat(list -> ((List<?>) list).isEmpty()));
    }

    @Test
    void testHandleRsusToAdd_RsuNotFound_NotFound() throws UnknownHostException {
        OrganizationPatch patch = minimalPatch();
        patch.setRsusToAdd(List.of("10.0.0.2"));

        when(authToken.isSuperUser()).thenReturn(true);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(testOrg));
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(organizationRepository.save(testOrg)).thenReturn(testOrg);
        InetAddress ip = InetAddress.getByName("10.0.0.2");
        when(rsuOrganizationRepository.findByRsuIpv4AddressAndOrganization(ip, testOrg))
                .thenReturn(Optional.empty());
        when(rsuRepository.findByIpv4Address(ip)).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.modifyOrganization(patch, authToken));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("RSU not found"));
    }

    @Test
    void testHandleRsusToAdd_Success_SaveAll() throws UnknownHostException {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setRsusToAdd(List.of("10.0.0.3"));

        InetAddress ip = InetAddress.getByName("10.0.0.3");
        Rsu rsu = new Rsu();
        rsu.setId(5);
        rsu.setIpv4Address(ip);

        when(rsuOrganizationRepository.findByRsuIpv4AddressAndOrganization(ip, testOrg))
                .thenReturn(Optional.empty());
        when(rsuRepository.findByIpv4Address(ip)).thenReturn(rsu);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RsuOrganization>> captor = ArgumentCaptor.forClass(List.class);
        service.modifyOrganization(patch, authToken);

        verify(rsuOrganizationRepository).saveAll(captor.capture());
        List<RsuOrganization> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertSame(rsu, saved.get(0).getRsu());
        assertSame(testOrg, saved.get(0).getOrganization());
    }

    // =========================================================================
    // rsusToRemove (step 8)
    // =========================================================================

    @Test
    void testRsusToRemove_EmptyList_NoDelete() {
        stubBaseFlow();

        service.modifyOrganization(minimalPatch(), authToken);

        verify(rsuOrganizationRepository, never()).deleteByRsuIpv4AddressesAndOrganization(any(), any());
    }

    @Test
    void testRsusToRemove_NonEmpty_BatchDeletes() throws UnknownHostException {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setRsusToRemove(List.of("10.0.1.1", "10.0.1.2"));

        InetAddress ip1 = InetAddress.getByName("10.0.1.1");
        InetAddress ip2 = InetAddress.getByName("10.0.1.2");

        service.modifyOrganization(patch, authToken);

        verify(rsuOrganizationRepository).deleteByRsuIpv4AddressesAndOrganization(
                List.of(ip1, ip2), testOrg);
    }

    @Test
    void testRsusToRemove_InvalidIp_ThrowsBadRequest() {
        // Do not use stubBaseFlow() — the mapper stub is never reached because
        // resolveIpAddress throws before the method returns.
        when(authToken.isSuperUser()).thenReturn(true);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(testOrg));
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(organizationRepository.save(testOrg)).thenReturn(testOrg);
        OrganizationPatch patch = minimalPatch();
        // "256.0.0.1" is not a valid IPv4 address and is not resolvable as a hostname
        patch.setRsusToRemove(List.of("256.0.0.1"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.modifyOrganization(patch, authToken));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // =========================================================================
    // handleIntersectionsToAdd
    // =========================================================================

    @Test
    void testHandleIntersectionsToAdd_EmptyList_NoOps() {
        stubBaseFlow();

        service.modifyOrganization(minimalPatch(), authToken);

        verify(intersectionOrganizationRepository, never()).saveAll(any());
    }

    @Test
    void testHandleIntersectionsToAdd_OrgNotFound_NotFound() {
        OrganizationPatch patch = minimalPatch();
        patch.setIntersectionsToAdd(List.of(1001));

        when(authToken.isSuperUser()).thenReturn(true);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.modifyOrganization(patch, authToken));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Organization not found"));
    }

    @Test
    void testHandleIntersectionsToAdd_AlreadyAssigned_Skipped() {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setIntersectionsToAdd(List.of(1001));

        when(intersectionOrganizationRepository
                .findByIntersection_IntersectionNumberAndOrganization("1001", testOrg))
                .thenReturn(Optional.of(new IntersectionOrganization()));

        service.modifyOrganization(patch, authToken);

        verifyNoInteractions(intersectionRepository); // intersection entity never loaded
        verify(intersectionOrganizationRepository).saveAll(argThat(list -> ((List<?>) list).isEmpty()));
    }

    @Test
    void testHandleIntersectionsToAdd_IntersectionNotFound_NotFound() {
        OrganizationPatch patch = minimalPatch();
        patch.setIntersectionsToAdd(List.of(9999));

        when(authToken.isSuperUser()).thenReturn(true);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(testOrg));
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(organizationRepository.save(testOrg)).thenReturn(testOrg);
        when(intersectionOrganizationRepository
                .findByIntersection_IntersectionNumberAndOrganization("9999", testOrg))
                .thenReturn(Optional.empty());
        when(intersectionRepository.findByIntersectionNumber("9999")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.modifyOrganization(patch, authToken));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Intersection not found"));
    }

    @Test
    void testHandleIntersectionsToAdd_Success_SaveAll() {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setIntersectionsToAdd(List.of(1234));

        Intersection intersection = new Intersection();
        intersection.setId(1);
        intersection.setIntersectionNumber("1234");

        when(intersectionOrganizationRepository
                .findByIntersection_IntersectionNumberAndOrganization("1234", testOrg))
                .thenReturn(Optional.empty());
        when(intersectionRepository.findByIntersectionNumber("1234")).thenReturn(Optional.of(intersection));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IntersectionOrganization>> captor = ArgumentCaptor.forClass(List.class);
        service.modifyOrganization(patch, authToken);

        verify(intersectionOrganizationRepository).saveAll(captor.capture());
        List<IntersectionOrganization> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertSame(intersection, saved.get(0).getIntersection());
        assertSame(testOrg, saved.get(0).getOrganization());
    }

    // =========================================================================
    // intersectionsToRemove (step 10)
    // =========================================================================

    @Test
    void testIntersectionsToRemove_EmptyList_NoDelete() {
        stubBaseFlow();

        service.modifyOrganization(minimalPatch(), authToken);

        verify(intersectionOrganizationRepository, never())
                .deleteByIntersectionNumbersAndOrganization(any(), any());
    }

    @Test
    void testIntersectionsToRemove_NonEmpty_BatchDeletes() {
        stubBaseFlow();
        OrganizationPatch patch = minimalPatch();
        patch.setIntersectionsToRemove(List.of(1001, 1002));

        service.modifyOrganization(patch, authToken);

        verify(intersectionOrganizationRepository).deleteByIntersectionNumbersAndOrganization(
                List.of("1001", "1002"), testOrg);
    }

    // =========================================================================
    // deleteOrganization
    // =========================================================================

    @Test
    void testDeleteOrganization_OrgNotFound_ThrowsNotFound() {
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteOrganization(testOrg.getId()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(rsuOrganizationRepository, never()).existsOrphanRsuInOrganization(any());
        verify(organizationRepository, never()).delete(any());
    }

    @Test
    void testDeleteOrganization_OrphanRsu_ThrowsConflict() {
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(rsuOrganizationRepository.existsOrphanRsuInOrganization(testOrg)).thenReturn(true);

        OrganizationManagementService.OrganizationHasDependentsException ex = assertThrows(
                OrganizationManagementService.OrganizationHasDependentsException.class,
                () -> service.deleteOrganization(testOrg.getId()));

        assertTrue(ex.getMessage().contains("RSU"));
        verify(intersectionOrganizationRepository, never()).existsOrphanIntersectionInOrganization(any());
        verify(userOrganizationRepository, never()).existsOrphanUserInOrganization(any());
        verify(organizationRepository, never()).delete(any());
    }

    @Test
    void testDeleteOrganization_OrphanIntersection_ThrowsConflict() {
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(rsuOrganizationRepository.existsOrphanRsuInOrganization(testOrg)).thenReturn(false);
        when(intersectionOrganizationRepository.existsOrphanIntersectionInOrganization(testOrg)).thenReturn(true);

        OrganizationManagementService.OrganizationHasDependentsException ex = assertThrows(
                OrganizationManagementService.OrganizationHasDependentsException.class,
                () -> service.deleteOrganization(testOrg.getId()));

        assertTrue(ex.getMessage().contains("Intersection"));
        verify(userOrganizationRepository, never()).existsOrphanUserInOrganization(any());
        verify(organizationRepository, never()).delete(any());
    }

    @Test
    void testDeleteOrganization_OrphanUser_ThrowsConflict() {
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(rsuOrganizationRepository.existsOrphanRsuInOrganization(testOrg)).thenReturn(false);
        when(intersectionOrganizationRepository.existsOrphanIntersectionInOrganization(testOrg)).thenReturn(false);
        when(userOrganizationRepository.existsOrphanUserInOrganization(testOrg)).thenReturn(true);

        OrganizationManagementService.OrganizationHasDependentsException ex = assertThrows(
                OrganizationManagementService.OrganizationHasDependentsException.class,
                () -> service.deleteOrganization(testOrg.getId()));

        assertTrue(ex.getMessage().contains("user"));
        verify(organizationRepository, never()).delete(any());
    }

    @Test
    void testDeleteOrganization_NoOrphans_DeletesJunctionTablesAndOrg() {
        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(rsuOrganizationRepository.existsOrphanRsuInOrganization(testOrg)).thenReturn(false);
        when(intersectionOrganizationRepository.existsOrphanIntersectionInOrganization(testOrg)).thenReturn(false);
        when(userOrganizationRepository.existsOrphanUserInOrganization(testOrg)).thenReturn(false);

        service.deleteOrganization(testOrg.getId());

        verify(userOrganizationRepository).deleteAllByOrganization(testOrg);
        verify(rsuOrganizationRepository).deleteAllByOrganization(testOrg);
        verify(intersectionOrganizationRepository).deleteAllByOrganization(testOrg);
        verify(organizationRepository).delete(testOrg);
    }

    @Test
    void testDeleteOrganization_NoOrphans_JunctionTablesDeletedBeforeOrg() {
        // Verify deletion order: junction tables first, then org record
        InOrder inOrder = inOrder(
                userOrganizationRepository,
                rsuOrganizationRepository,
                intersectionOrganizationRepository,
                organizationRepository);

        when(organizationRepository.findById(testOrg.getId())).thenReturn(Optional.of(testOrg));
        when(rsuOrganizationRepository.existsOrphanRsuInOrganization(testOrg)).thenReturn(false);
        when(intersectionOrganizationRepository.existsOrphanIntersectionInOrganization(testOrg)).thenReturn(false);
        when(userOrganizationRepository.existsOrphanUserInOrganization(testOrg)).thenReturn(false);

        service.deleteOrganization(testOrg.getId());

        inOrder.verify(userOrganizationRepository).deleteAllByOrganization(testOrg);
        inOrder.verify(rsuOrganizationRepository).deleteAllByOrganization(testOrg);
        inOrder.verify(intersectionOrganizationRepository).deleteAllByOrganization(testOrg);
        inOrder.verify(organizationRepository).delete(testOrg);
    }
}
