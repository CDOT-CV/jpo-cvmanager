package us.dot.its.jpo.ode.api.services;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import us.dot.its.jpo.ode.api.controllers.credentials.SnmpCredentialController;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpCredentialRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnmpCredentialManagementServiceTest {

    @Mock
    SnmpCredentialRepository mockSnmpCredentialRepository;

    @Mock
    OrganizationRepository mockOrganizationRepository;

    @Mock
    PermissionService mockPermissionService;

    @InjectMocks
    SnmpCredentialManagementService snmpCredentialManagementService;

    @Test
    void testCreate_Success()
            throws SnmpCredentialManagementService.SnmpCredentialAlreadyExistsException, EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        int orgId = 1;
        SnmpCredentialController.SnmpCredentialCreateRequest request = new SnmpCredentialController.SnmpCredentialCreateRequest(
                nickname, username, password, orgId);

        Organization mockOrganization = mock(Organization.class);
        when(mockOrganization.getId()).thenReturn(orgId);
        when(mockOrganizationRepository.findById(orgId)).thenReturn(Optional.of(mockOrganization));

        SnmpCredential expectedSnmpCredential = new SnmpCredential();
        expectedSnmpCredential.setNickname(nickname);
        expectedSnmpCredential.setUsername(username);
        expectedSnmpCredential.setPassword(password);
        expectedSnmpCredential.setOwnerOrganization(mockOrganization);
        when(mockSnmpCredentialRepository.save(any())).thenReturn(expectedSnmpCredential);

        // Act
        SnmpCredential snmpCredential = snmpCredentialManagementService.create(request);

        // Assert
        assertEquals(nickname, snmpCredential.getNickname());
        assertEquals(username, snmpCredential.getUsername());
        assertEquals(password, snmpCredential.getPassword());
        assertEquals(orgId, snmpCredential.getOwnerOrganization().getId());
        verify(mockSnmpCredentialRepository).save(any());
    }

    @Test
    void testCreate_Failure_AlreadyExists() {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        int orgId = 1;
        SnmpCredentialController.SnmpCredentialCreateRequest request = new SnmpCredentialController.SnmpCredentialCreateRequest(
                nickname, username, password, orgId);

        when(mockSnmpCredentialRepository.existsByNickname(nickname)).thenReturn(true);

        // Act & Assert
        assertThrows(SnmpCredentialManagementService.SnmpCredentialAlreadyExistsException.class,
                () -> snmpCredentialManagementService.create(request));
    }

    @Test
    void testCreate_Failure_OrganizationNotFound() {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        int orgId = 1;
        SnmpCredentialController.SnmpCredentialCreateRequest request = new SnmpCredentialController.SnmpCredentialCreateRequest(
                nickname, username, password, orgId);

        when(mockOrganizationRepository.findById(orgId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> snmpCredentialManagementService.create(request));
    }

    @Test
    void testGetByNickname_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        int orgId = 1;
        Organization mockOrganization = mock(Organization.class);
        lenient().when(mockOrganization.getId()).thenReturn(orgId);

        SnmpCredential expectedSnmpCredential = new SnmpCredential();
        expectedSnmpCredential.setOwnerOrganization(mockOrganization);
        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(expectedSnmpCredential));

        // Act
        SnmpCredential snmpCredential = snmpCredentialManagementService.getByNickname(nickname);

        // Assert
        assertNotNull(snmpCredential);
        assertEquals(expectedSnmpCredential, snmpCredential);
        verify(mockSnmpCredentialRepository).findByNickname(nickname);
    }

    @Test
    void testGetByNickname_Failure_NotFound() {
        // Arrange
        String nickname = "nickname";
        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> snmpCredentialManagementService.getByNickname(nickname));
    }

    @Test
    void testUpdate_ChangePassword_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        int organizationId = 1;
        String newPassword = "mynewpassword";
        SnmpCredentialController.SnmpCredentialPatch patch = new SnmpCredentialController.SnmpCredentialPatch(nickname);
        patch.setPassword(newPassword);

        SnmpCredential existingCredential = new SnmpCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);

        Organization mockOrganization = mock(Organization.class);
        lenient().when(mockOrganization.getId()).thenReturn(organizationId);
        lenient().when(mockOrganization.getName()).thenReturn(organization);
        existingCredential.setOwnerOrganization(mockOrganization);

        SnmpCredential expectedCredential = new SnmpCredential();
        expectedCredential.setNickname(nickname);
        expectedCredential.setUsername(username);
        expectedCredential.setPassword(newPassword);
        expectedCredential.setOwnerOrganization(mockOrganization);

        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));
        when(mockSnmpCredentialRepository.save(any())).thenReturn(expectedCredential);

        // Act
        SnmpCredential updatedCredential = snmpCredentialManagementService.update(patch);

        // Assert
        assertNotNull(updatedCredential);
        assertEquals(nickname, updatedCredential.getNickname());
        assertEquals(username, updatedCredential.getUsername());
        assertEquals(newPassword, updatedCredential.getPassword());
        assertEquals(organizationId, updatedCredential.getOwnerOrganization().getId());
        verify(mockSnmpCredentialRepository).findByNickname(nickname);
        verify(mockSnmpCredentialRepository).save(any());
    }

    @Test
    void testUpdate_ChangeOrganization_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        int oldOrganizationId = 1;
        int newOrganizationId = 2;
        SnmpCredentialController.SnmpCredentialPatch patch = new SnmpCredentialController.SnmpCredentialPatch(nickname);
        patch.setOrganization(newOrganizationId);

        SnmpCredential existingCredential = new SnmpCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);

        Organization mockOldOrganization = mock(Organization.class);
        lenient().when(mockOldOrganization.getId()).thenReturn(oldOrganizationId);
        existingCredential.setOwnerOrganization(mockOldOrganization);

        Organization mockNewOrganization = mock(Organization.class);
        lenient().when(mockNewOrganization.getId()).thenReturn(newOrganizationId);

        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));
        when(mockSnmpCredentialRepository.save(any())).thenReturn(existingCredential);

        when(mockOrganizationRepository.findById(newOrganizationId)).thenReturn(Optional.of(mockNewOrganization));

        // Act
        SnmpCredential updatedCredential = snmpCredentialManagementService.update(patch);

        // Assert
        assertNotNull(updatedCredential);
        assertEquals(nickname, updatedCredential.getNickname());
        assertEquals(username, updatedCredential.getUsername());
        assertEquals(password, updatedCredential.getPassword());
        assertEquals(newOrganizationId, updatedCredential.getOwnerOrganization().getId());
        verify(mockSnmpCredentialRepository).findByNickname(nickname);
    }

    @Test
    void testUpdate_ChangeUsername_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        int organizationId = 1;
        String newUsername = "newUsername";
        SnmpCredentialController.SnmpCredentialPatch patch = new SnmpCredentialController.SnmpCredentialPatch(nickname);
        patch.setUsername(newUsername);

        SnmpCredential existingCredential = new SnmpCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);

        Organization mockOrganization = mock(Organization.class);
        lenient().when(mockOrganization.getId()).thenReturn(organizationId);
        lenient().when(mockOrganization.getName()).thenReturn(organization);
        existingCredential.setOwnerOrganization(mockOrganization);

        SnmpCredential expectedCredential = new SnmpCredential();
        expectedCredential.setNickname(nickname);
        expectedCredential.setUsername(newUsername);
        expectedCredential.setPassword(password);
        expectedCredential.setOwnerOrganization(mockOrganization);

        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));
        when(mockSnmpCredentialRepository.save(any())).thenReturn(expectedCredential);

        // Act
        SnmpCredential snmpCredential = snmpCredentialManagementService.update(patch);

        // Assert
        assertNotNull(snmpCredential);
        assertEquals(nickname, snmpCredential.getNickname());
        assertEquals(newUsername, snmpCredential.getUsername());
        assertEquals(password, snmpCredential.getPassword());
        assertEquals(organizationId, snmpCredential.getOwnerOrganization().getId());
        verify(mockSnmpCredentialRepository).findByNickname(nickname);
        verify(mockSnmpCredentialRepository).save(any());
    }

    @Test
    void testUpdate_ChangeOrganization_Failure_OrganizationNotFound() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        int orgId = 1;
        int newOrgId = 2;
        SnmpCredentialController.SnmpCredentialPatch patch = new SnmpCredentialController.SnmpCredentialPatch(nickname);
        patch.setOrganization(newOrgId);

        SnmpCredential existingCredential = new SnmpCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);

        Organization mockOrganization = mock(Organization.class);
        lenient().when(mockOrganization.getId()).thenReturn(orgId);
        existingCredential.setOwnerOrganization(mockOrganization);

        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));
        when(mockOrganizationRepository.findById(newOrgId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> snmpCredentialManagementService.update(patch));
    }

    @Test
    void testDeleteByNickname_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String organization = "organization";
        int organizationId = 1;

        SnmpCredential existingCredential = new SnmpCredential();
        Organization mockOrganization = mock(Organization.class);
        lenient().when(mockOrganization.getId()).thenReturn(organizationId);
        lenient().when(mockOrganization.getName()).thenReturn(organization);
        existingCredential.setOwnerOrganization(mockOrganization);
        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));

        // Act
        snmpCredentialManagementService.deleteByNickname(nickname);

        // Assert
        verify(mockSnmpCredentialRepository).delete(existingCredential);
    }

    @Test
    void testDeleteByNickname_Failure_CredentialNotFound() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> snmpCredentialManagementService.deleteByNickname(nickname));
    }
}