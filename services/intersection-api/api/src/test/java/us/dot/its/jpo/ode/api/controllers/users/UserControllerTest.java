package us.dot.its.jpo.ode.api.controllers.users;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.keycloak.CvManagerAuthToken;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.User;
import us.dot.its.jpo.ode.api.models.users.ModifyUserAllowedSelections;
import us.dot.its.jpo.ode.api.models.users.UserDto;
import us.dot.its.jpo.ode.api.models.users.UserOrganizationDto;
import us.dot.its.jpo.ode.api.models.users.UserPatch;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.UserManagementService;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserManagementService userManagementService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private CvManagerAuthToken authToken;

    @InjectMocks
    private UserController userController;

    private UserDto testUserDto;
    private String testToken = "Bearer mock-jwt-token";

    private Organization sampleOrganization;
    private Organization org2;
    private Organization org3;

    @BeforeEach
    void setUp() {
        // Set up test user DTO
        testUserDto = new UserDto("test@example.com", "Test", "User", false, List.of());

        // Set up mock request context
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", testToken);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        sampleOrganization = new Organization();
        sampleOrganization.setId(1);
        sampleOrganization.setName("TestOrg");

        org2 = new Organization();
        org2.setId(2);
        org2.setName("TestOrg2");

        org3 = new Organization();
        org3.setId(3);
        org3.setName("TestOrg3");
    }

    // ==================== getUsers Tests ====================

    @Test
    void testGetUsers_Success() {
        String search = "";
        Pageable pageable = PageRequest.of(0, 100);
        List<UserDto> users = List.of(testUserDto);
        Page<UserDto> userPage = new PageImpl<>(users, pageable, 1);

        when(userManagementService.getUsers(eq(sampleOrganization), eq(search), any(Pageable.class)))
                .thenReturn(userPage);
        when(permissionService.getOrganizationById(sampleOrganization.getId())).thenReturn(sampleOrganization);

        Page<UserDto> result = userController.getUsers(sampleOrganization.getId(), search, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("test@example.com", result.getContent().get(0).getEmail());
        verify(userManagementService).getUsers(eq(sampleOrganization), eq(search), any(Pageable.class));
    }

    @Test
    void testGetUsers_WithSearch() {
        String search = "test";
        Pageable pageable = PageRequest.of(0, 100);
        List<UserDto> users = List.of(testUserDto);
        Page<UserDto> userPage = new PageImpl<>(users, pageable, 1);

        when(userManagementService.getUsers(eq(sampleOrganization), eq(search), any(Pageable.class)))
                .thenReturn(userPage);
        when(permissionService.getOrganizationById(sampleOrganization.getId())).thenReturn(sampleOrganization);

        Page<UserDto> result = userController.getUsers(sampleOrganization.getId(), search, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(userManagementService).getUsers(eq(sampleOrganization), eq(search), any(Pageable.class));
    }

    @Test
    void testGetUsers_WithSorting_FirstName() {
        String search = "";
        Pageable pageable = PageRequest.of(0, 100, Sort.by("first_name").ascending());
        List<UserDto> users = List.of(testUserDto);
        Page<UserDto> userPage = new PageImpl<>(users, pageable, 1);

        when(userManagementService.getUsers(eq(sampleOrganization), eq(search), any(Pageable.class)))
                .thenReturn(userPage);
        when(permissionService.getOrganizationById(sampleOrganization.getId())).thenReturn(sampleOrganization);

        Page<UserDto> result = userController.getUsers(sampleOrganization.getId(), search, pageable);

        assertNotNull(result);
        verify(userManagementService).getUsers(eq(sampleOrganization), eq(search), argThat(p -> {
            Sort.Order order = p.getSort().iterator().next();
            return "firstName".equals(order.getProperty()) && order.isAscending();
        }));
    }

    @Test
    void testGetUsers_WithSorting_LastName() {
        String search = "";
        Pageable pageable = PageRequest.of(0, 100, Sort.by("last_name").descending());
        List<UserDto> users = List.of(testUserDto);
        Page<UserDto> userPage = new PageImpl<>(users, pageable, 1);

        when(userManagementService.getUsers(eq(sampleOrganization), eq(search), any(Pageable.class)))
                .thenReturn(userPage);
        when(permissionService.getOrganizationById(sampleOrganization.getId())).thenReturn(sampleOrganization);

        Page<UserDto> result = userController.getUsers(sampleOrganization.getId(), search, pageable);

        assertNotNull(result);
        verify(userManagementService).getUsers(eq(sampleOrganization), eq(search), argThat(p -> {
            Sort.Order order = p.getSort().iterator().next();
            return "lastName".equals(order.getProperty()) && order.isDescending();
        }));
    }

    @Test
    void testGetUsers_WithSorting_SuperUser() {
        String search = "";
        Pageable pageable = PageRequest.of(0, 100, Sort.by("super_user").ascending());
        List<UserDto> users = List.of(testUserDto);
        Page<UserDto> userPage = new PageImpl<>(users, pageable, 1);

        when(userManagementService.getUsers(eq(sampleOrganization), eq(search), any(Pageable.class)))
                .thenReturn(userPage);
        when(permissionService.getOrganizationById(sampleOrganization.getId())).thenReturn(sampleOrganization);

        Page<UserDto> result = userController.getUsers(sampleOrganization.getId(), search, pageable);

        assertNotNull(result);
        verify(userManagementService).getUsers(eq(sampleOrganization), eq(search), argThat(p -> {
            Sort.Order order = p.getSort().iterator().next();
            return "superUser".equals(order.getProperty());
        }));
    }

    @Test
    void testGetUsers_WithSorting_UnmappedField() {
        String search = "";
        Pageable pageable = PageRequest.of(0, 100, Sort.by("email").ascending());
        List<UserDto> users = List.of(testUserDto);
        Page<UserDto> userPage = new PageImpl<>(users, pageable, 1);

        when(userManagementService.getUsers(eq(sampleOrganization), eq(search), any(Pageable.class)))
                .thenReturn(userPage);
        when(permissionService.getOrganizationById(sampleOrganization.getId())).thenReturn(sampleOrganization);

        Page<UserDto> result = userController.getUsers(sampleOrganization.getId(), search, pageable);

        assertNotNull(result);
        // Should keep original field name if not in mapping
        verify(userManagementService).getUsers(eq(sampleOrganization), eq(search), argThat(p -> {
            Sort.Order order = p.getSort().iterator().next();
            return "email".equals(order.getProperty());
        }));
    }

    @Test
    void testGetUsers_NoSorting() {
        String search = "";
        Pageable pageable = PageRequest.of(0, 100);
        List<UserDto> users = List.of(testUserDto);
        Page<UserDto> userPage = new PageImpl<>(users, pageable, 1);

        when(userManagementService.getUsers(eq(sampleOrganization), eq(search), any(Pageable.class)))
                .thenReturn(userPage);
        when(permissionService.getOrganizationById(sampleOrganization.getId())).thenReturn(sampleOrganization);

        Page<UserDto> result = userController.getUsers(sampleOrganization.getId(), search, pageable);

        assertNotNull(result);
        verify(userManagementService).getUsers(eq(sampleOrganization), eq(search),
                argThat(p -> !p.getSort().isSorted()));
    }

    @Test
    void testGetUsers_EmptyResults() {
        String search = "nonexistent";
        Pageable pageable = PageRequest.of(0, 100);
        Page<UserDto> userPage = new PageImpl<>(new ArrayList<>(), pageable, 0);

        when(userManagementService.getUsers(eq(sampleOrganization), eq(search), any(Pageable.class)))
                .thenReturn(userPage);
        when(permissionService.getOrganizationById(sampleOrganization.getId())).thenReturn(sampleOrganization);

        Page<UserDto> result = userController.getUsers(sampleOrganization.getId(), search, pageable);

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void testGetUsers_Pagination() {
        String search = "";
        Pageable pageable = PageRequest.of(1, 25); // Page 2, size 25
        List<UserDto> users = List.of(testUserDto);
        Page<UserDto> userPage = new PageImpl<>(users, pageable, 100); // 100 total

        when(userManagementService.getUsers(eq(sampleOrganization), eq(search), any(Pageable.class)))
                .thenReturn(userPage);
        when(permissionService.getOrganizationById(sampleOrganization.getId())).thenReturn(sampleOrganization);

        Page<UserDto> result = userController.getUsers(sampleOrganization.getId(), search, pageable);

        assertNotNull(result);
        assertEquals(1, result.getNumber()); // Page number
        assertEquals(25, result.getSize()); // Page size
        assertEquals(100, result.getTotalElements()); // Total elements
        assertEquals(4, result.getTotalPages()); // Total pages (100/25)
    }

    // ==================== getSingleUser Tests ====================

    @Test
    void testGetSingleUser_Success() {
        String email = "test@example.com";
        when(userManagementService.getUser(email)).thenReturn(testUserDto);

        UserDto result = userController.getSingleUser(email);

        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        verify(userManagementService).getUser(email);
    }

    @Test
    void testGetSingleUser_DifferentEmail() {
        String email = "another@example.com";
        UserDto anotherUser = new UserDto(email, "Another", "User", false, List.of());
        when(userManagementService.getUser(email)).thenReturn(anotherUser);

        UserDto result = userController.getSingleUser(email);

        assertNotNull(result);
        assertEquals(email, result.getEmail());
        verify(userManagementService).getUser(email);
    }

    // ==================== getAllowedSelections Tests ====================

    @Test
    void testGetAllowedSelections_Success() {
        ModifyUserAllowedSelections allowedSelections = new ModifyUserAllowedSelections();
        allowedSelections.setRoles(List.of("admin", "operator", "user"));
        allowedSelections.setOrganizations(List.of(1, 2));

        when(userManagementService.getAllowedSelections(anyList()))
                .thenReturn(allowedSelections);

        when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(sampleOrganization));

        ModifyUserAllowedSelections result = userController.getAllowedSelections();

        assertNotNull(result);
        assertEquals(3, result.getRoles().size());
        assertEquals(2, result.getOrganizations().size());
        assertTrue(result.getRoles().contains("admin"));
        assertTrue(result.getOrganizations().contains(1));
        verify(userManagementService).getAllowedSelections(List.of(sampleOrganization));
    }

    @Test
    void testGetAllowedSelections_EmptySelections() {
        ModifyUserAllowedSelections allowedSelections = new ModifyUserAllowedSelections();
        allowedSelections.setRoles(new ArrayList<>());
        allowedSelections.setOrganizations(new ArrayList<>());

        when(userManagementService.getAllowedSelections(anyList()))
                .thenReturn(allowedSelections);

        when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(sampleOrganization));

        ModifyUserAllowedSelections result = userController.getAllowedSelections();

        assertNotNull(result);
        assertTrue(result.getRoles().isEmpty());
        assertTrue(result.getOrganizations().isEmpty());
    }

    // ==================== createUser Tests ====================

    @Test
    void testCreateUser_Success() {
        UserOrganizationDto org1 = new UserOrganizationDto();
        org1.setOrganization(1);
        org1.setRole("USER");

        UserDto newUser = new UserDto(
                "newuser@example.com",
                "New",
                "User",
                false,
                List.of(org1));

        when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(sampleOrganization));
        when(userManagementService.createUser(newUser, List.of(sampleOrganization))).thenReturn(new User());

        userController.createUser(newUser);

        verify(userManagementService).createUser(newUser, List.of(sampleOrganization));
    }

    @Test
    void testCreateUser_MultipleOrganizations() {
        UserOrganizationDto org1 = new UserOrganizationDto();
        org1.setOrganization(1);
        org1.setRole("USER");

        UserOrganizationDto org2 = new UserOrganizationDto();
        org2.setOrganization(2);
        org2.setRole("OPERATOR");

        UserDto newUser = new UserDto(
                "newuser@example.com",
                "New",
                "User",
                false,
                List.of(org1, org2));

        when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(sampleOrganization));
        when(userManagementService.createUser(newUser, List.of(sampleOrganization))).thenReturn(new User());

        userController.createUser(newUser);

        verify(userManagementService).createUser(newUser, List.of(sampleOrganization));
    }

    @Test
    void testCreateUser_WithSuperUserFlag() {
        UserOrganizationDto org = new UserOrganizationDto();
        org.setOrganization(1);
        org.setRole("ADMIN");

        UserDto newUser = new UserDto(
                "admin@example.com",
                "Admin",
                "User",
                true,
                List.of(org));

        when(permissionService.isSuperUser()).thenReturn(true);
        when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(sampleOrganization));
        when(userManagementService.createUser(newUser, List.of(sampleOrganization))).thenReturn(new User());

        userController.createUser(newUser);

        assertTrue(newUser.getSuperUser());
        verify(userManagementService).createUser(newUser, List.of(sampleOrganization));
    }

    @Test
    void testCreateSuperUser_WithoutSuperUserFlag() {
        UserOrganizationDto org = new UserOrganizationDto();
        org.setOrganization(1);
        org.setRole("ADMIN");

        UserDto newUser = new UserDto(
                "admin@example.com",
                        "Admin",
                "User",
                true,
                List.of(org));

        when(permissionService.isSuperUser()).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                        () -> userController.createUser(newUser));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Non-super user not qualified to create super user", ex.getReason());
        verify(userManagementService, never()).createUser(any(), anyList());
    }

    @Test
    void testCreateUser_WithDifferentRoles() {
        UserOrganizationDto orgAdmin = new UserOrganizationDto();
        orgAdmin.setOrganization(1);
        orgAdmin.setRole("ADMIN");

        UserOrganizationDto orgOperator = new UserOrganizationDto();
        orgOperator.setOrganization(2);
        orgOperator.setRole("OPERATOR");

        UserOrganizationDto orgUser = new UserOrganizationDto();
        orgUser.setOrganization(3);
        orgUser.setRole("USER");

        UserDto newUser = new UserDto(
                "newuser@example.com",
                "New",
                "User",
                false,
                List.of(orgAdmin, orgOperator, orgUser));

        when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(sampleOrganization, org2, org3));
        when(userManagementService.createUser(newUser, List.of(sampleOrganization, org2, org3))).thenReturn(new User());

        userController.createUser(newUser);

        verify(userManagementService).createUser(newUser, List.of(sampleOrganization, org2, org3));
    }

    @Test
    void testCreateUser_WithSpecialCharactersInEmail() {
        UserOrganizationDto org = new UserOrganizationDto();
        org.setOrganization(1);
        org.setRole("USER");

        UserDto newUser = new UserDto(
                "new.user+tag@example.co.uk",
                "New",
                "User",
                false,
                List.of(org));

        when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(sampleOrganization));
        when(userManagementService.createUser(newUser, List.of(sampleOrganization))).thenReturn(new User());

        userController.createUser(newUser);

        assertEquals("new.user+tag@example.co.uk", newUser.getEmail());
        verify(userManagementService).createUser(newUser, List.of(sampleOrganization));
    }

    @Test
    void testCreateUser_ServiceLayerValidationHandled() {
        UserOrganizationDto org = new UserOrganizationDto();
        org.setOrganization(1);
        org.setRole("USER");

        UserDto newUser = new UserDto(
                "duplicate@example.com",
                "New",
                "User",
                false,
                List.of(org));

        when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(sampleOrganization));
        doThrow(new IllegalArgumentException("User with email already exists"))
                .when(userManagementService).createUser(newUser, List.of(sampleOrganization));

        assertThrows(IllegalArgumentException.class, () -> userController.createUser(newUser));
        verify(userManagementService).createUser(newUser, List.of(sampleOrganization));
    }

    // ==================== modifyUser Tests ====================

    @Test
    void testModifyUser_Success() {
        String email = "test@example.com";
        UserPatch userPatch = new UserPatch();
        userPatch.setFirstName("Updated");
        userPatch.setLastName("Name");

        when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(sampleOrganization));
        when(userManagementService.modifyUser(email, userPatch, List.of(sampleOrganization)))
                .thenReturn(testUserDto);

        ResponseEntity<Void> result = userController.modifyUser(email, userPatch);

        assertNotNull(result);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(userManagementService).modifyUser(email, userPatch, List.of(sampleOrganization));
    }

    @Test
    void testModifyUser_WithOrganizationChanges() {
        String email = "test@example.com";
        UserPatch userPatch = new UserPatch();
        userPatch.setOrganizationsToAdd(List.of());
        userPatch.setOrganizationsToRemove(List.of());

        when(permissionService.getCvManagerAuthToken()).thenReturn(authToken);
        when(authToken.getQualifiedOrgList(UserRole.ADMIN)).thenReturn(List.of(sampleOrganization));
        when(userManagementService.modifyUser(email, userPatch, List.of(sampleOrganization)))
                .thenReturn(testUserDto);

        ResponseEntity<Void> result = userController.modifyUser(email, userPatch);

        assertNotNull(result);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(userManagementService).modifyUser(email, userPatch, List.of(sampleOrganization));
    }

    // ==================== deleteUser Tests ====================

    @Test
    void testDeleteUser_Success() {
        String email = "test@example.com";
        doNothing().when(userManagementService).deleteUserByEmail(email);

        ResponseEntity<Void> result = userController.deleteUser(email);

        assertNotNull(result);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(userManagementService).deleteUserByEmail(email);
    }

    @Test
    void testDeleteUser_DifferentEmail() {
        String email = "another@example.com";
        doNothing().when(userManagementService).deleteUserByEmail(email);

        ResponseEntity<Void> result = userController.deleteUser(email);

        assertNotNull(result);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(userManagementService).deleteUserByEmail(email);
    }

    // ==================== deleteUsers Tests ====================

    @Test
    void testDeleteUsers_Success() {
        List<String> emails = List.of("test1@example.com", "test2@example.com");
        doNothing().when(userManagementService).deleteMultipleUsersByEmail(emails);

        ResponseEntity<Void> result = userController.deleteUsers(emails);

        assertNotNull(result);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(userManagementService).deleteMultipleUsersByEmail(emails);
    }

    @Test
    void testDeleteUsers_SingleUser() {
        List<String> emails = List.of("test@example.com");
        doNothing().when(userManagementService).deleteMultipleUsersByEmail(emails);

        ResponseEntity<Void> result = userController.deleteUsers(emails);

        assertNotNull(result);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(userManagementService).deleteMultipleUsersByEmail(emails);
    }

    @Test
    void testDeleteUsers_MultipleUsers() {
        List<String> emails = List.of(
                "test1@example.com",
                "test2@example.com",
                "test3@example.com",
                "test4@example.com");
        doNothing().when(userManagementService).deleteMultipleUsersByEmail(emails);

        ResponseEntity<Void> result = userController.deleteUsers(emails);

        assertNotNull(result);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(userManagementService).deleteMultipleUsersByEmail(emails);
    }

    @Test
    void testDeleteUsers_EmptyList() {
        List<String> emails = new ArrayList<>();
        doNothing().when(userManagementService).deleteMultipleUsersByEmail(emails);

        ResponseEntity<Void> result = userController.deleteUsers(emails);

        assertNotNull(result);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(userManagementService).deleteMultipleUsersByEmail(emails);
    }
}