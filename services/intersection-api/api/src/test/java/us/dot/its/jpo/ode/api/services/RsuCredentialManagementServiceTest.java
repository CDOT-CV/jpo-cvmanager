package us.dot.its.jpo.ode.api.services;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import us.dot.its.jpo.ode.api.controllers.credentials.RsuCredentialController;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuCredentialRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RsuCredentialManagementServiceTest {

    @Mock
    RsuCredentialRepository mockRsuCredentialRepository;

    @Mock
    OrganizationRepository mockOrganizationRepository;

    @InjectMocks
    RsuCredentialManagementService rsuCredentialManagementService;

    @Test
    void testCreate_Success() throws RsuCredentialManagementService.RsuCredentialAlreadyExistsException, EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        int ownerOrganizationId = 1;
        RsuCredentialController.RsuCredentialCreateRequest request = new RsuCredentialController.RsuCredentialCreateRequest(nickname, username, password, organization);

        Organization mockOrganization = mock(Organization.class);
        when(mockOrganization.getId()).thenReturn(ownerOrganizationId);
        when(mockOrganizationRepository.findByName(organization)).thenReturn(Optional.of(mockOrganization));

        RsuCredential mockRsuCredential = new RsuCredential();
        mockRsuCredential.setNickname(nickname);
        mockRsuCredential.setUsername(username);
        mockRsuCredential.setPassword(password);
        mockRsuCredential.setOwnerOrganization(mockOrganization);
        when(mockRsuCredentialRepository.save(any())).thenReturn(mockRsuCredential);

        // Act
        RsuCredential rsuCredential = rsuCredentialManagementService.create(organization, request);

        // Assert
        assertNotNull(rsuCredential);
        assertEquals(nickname, rsuCredential.getNickname());
        assertEquals(username, rsuCredential.getUsername());
        assertEquals(password, rsuCredential.getPassword());
        assertEquals(1, rsuCredential.getOwnerOrganization().getId());
        verify(mockRsuCredentialRepository).save(any());
    }

    @Test
    void testCreate_Failure_AlreadyExists() {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        RsuCredentialController.RsuCredentialCreateRequest request = new RsuCredentialController.RsuCredentialCreateRequest(nickname, username, password, organization);

        when(mockRsuCredentialRepository.existsByNickname(nickname)).thenReturn(true);

        // Act & Assert
        assertThrows(RsuCredentialManagementService.RsuCredentialAlreadyExistsException.class, () -> rsuCredentialManagementService.create(organization, request));
    }

    @Test
    void testCreate_Failure_OrganizationNotFound() {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        RsuCredentialController.RsuCredentialCreateRequest request = new RsuCredentialController.RsuCredentialCreateRequest(nickname, username, password, organization);

        when(mockOrganizationRepository.findByName(organization)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> rsuCredentialManagementService.create(organization, request));
    }

    @Test
    void testGetByNickname_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String organization = "organization";
        int organizationId = 1;
        RsuCredential mockRsuCredential = new RsuCredential();
        Organization mockOrganization = mock(Organization.class);
        when(mockOrganization.getId()).thenReturn(organizationId);
        mockRsuCredential.setOwnerOrganization(mockOrganization);
        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(mockRsuCredential));

        when(mockOrganizationRepository.findByName(organization)).thenReturn(Optional.of(mockOrganization));

        // Act
        RsuCredential rsuCredential = rsuCredentialManagementService.getByNickname(organization, nickname);

        // Assert
        assertNotNull(rsuCredential);
        assertEquals(mockRsuCredential, rsuCredential);
        verify(mockRsuCredentialRepository).findByNickname(nickname);
    }

    @Test
    void testGetByNickname_Failure_NotFound() {
        // Arrange
        String nickname = "nickname";
        String organization = "organization";
        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> rsuCredentialManagementService.getByNickname(organization, nickname));
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
        RsuCredentialController.RsuCredentialPatch rsuCredentialPatch = new RsuCredentialController.RsuCredentialPatch(nickname);
        rsuCredentialPatch.setPassword(newPassword);

        RsuCredential existingCredential = new RsuCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);
        
        Organization mockOrganization = mock(Organization.class);
        when(mockOrganization.getId()).thenReturn(organizationId);
        existingCredential.setOwnerOrganization(mockOrganization);

        RsuCredential expectedCredential = new RsuCredential();
        expectedCredential.setNickname(nickname);
        expectedCredential.setUsername(username);
        expectedCredential.setPassword(newPassword);
        expectedCredential.setOwnerOrganization(mockOrganization);

        when(mockOrganizationRepository.findByName(organization)).thenReturn(Optional.of(mockOrganization));

        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));
        when(mockRsuCredentialRepository.save(any())).thenReturn(expectedCredential);

        // Act
        RsuCredential updatedCredential = rsuCredentialManagementService.update(organization, rsuCredentialPatch);

        // Assert
        assertNotNull(updatedCredential);
        assertEquals(nickname, updatedCredential.getNickname());
        assertEquals(username, updatedCredential.getUsername());
        assertEquals(newPassword, updatedCredential.getPassword());
        verify(mockRsuCredentialRepository).findByNickname(nickname);
        verify(mockRsuCredentialRepository).save(any());
    }

    @Test
    void testUpdate_ChangeOrganization_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        int organizationId = 1;
        String newOrganization = "organization";
        int newOrganizationId = 1;
        RsuCredentialController.RsuCredentialPatch rsuCredentialPatch = new RsuCredentialController.RsuCredentialPatch(nickname);
        rsuCredentialPatch.setOrganization(newOrganization);

        RsuCredential existingCredential = new RsuCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);
        
        Organization mockOldOrganization = mock(Organization.class);
        when(mockOldOrganization.getId()).thenReturn(organizationId);
        existingCredential.setOwnerOrganization(mockOldOrganization);

        RsuCredential expectedCredential = new RsuCredential();
        expectedCredential.setNickname(nickname);
        expectedCredential.setUsername(username);
        expectedCredential.setPassword(password);
        
        Organization mockNewOrganization = mock(Organization.class);
        when(mockNewOrganization.getId()).thenReturn(newOrganizationId);
        expectedCredential.setOwnerOrganization(mockNewOrganization);

        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));
        when(mockRsuCredentialRepository.save(any())).thenReturn(expectedCredential);

        Organization mockOrganization = mock(Organization.class);
        when(mockOrganization.getId()).thenReturn(organizationId);
        when(mockOrganizationRepository.findByName(organization)).thenReturn(Optional.of(mockOrganization));

       // Act
       RsuCredential updatedCredential = rsuCredentialManagementService.update(organization, rsuCredentialPatch);

       // Assert
       assertNotNull(updatedCredential);
       assertEquals(nickname, updatedCredential.getNickname());
       assertEquals(username, updatedCredential.getUsername());
       assertEquals(password, updatedCredential.getPassword());
       assertEquals(newOrganizationId, updatedCredential.getOwnerOrganization().getId());
       verify(mockRsuCredentialRepository).findByNickname(nickname);
       verify(mockRsuCredentialRepository).save(any());
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
        RsuCredentialController.RsuCredentialPatch rsuCredentialPatch = new RsuCredentialController.RsuCredentialPatch(nickname);
        rsuCredentialPatch.setUsername(newUsername);

        RsuCredential existingCredential = new RsuCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);
        
        Organization mockOrganization = mock(Organization.class);
        when(mockOrganization.getId()).thenReturn(organizationId);
        existingCredential.setOwnerOrganization(mockOrganization);

        RsuCredential expectedCredential = new RsuCredential();
        expectedCredential.setNickname(nickname);
        expectedCredential.setUsername(newUsername);
        expectedCredential.setPassword(password);
        expectedCredential.setOwnerOrganization(mockOrganization);

        when(mockOrganizationRepository.findByName(organization)).thenReturn(Optional.of(mockOrganization));

        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));

        when(mockRsuCredentialRepository.save(any())).thenReturn(expectedCredential);

        // Act
        RsuCredential updatedCredential = rsuCredentialManagementService.update(organization, rsuCredentialPatch);

        // Assert
        assertNotNull(updatedCredential);
        assertEquals(nickname, updatedCredential.getNickname());
        assertEquals(newUsername, updatedCredential.getUsername());
        assertEquals(password, updatedCredential.getPassword());
        assertEquals(organizationId, updatedCredential.getOwnerOrganization().getId());
        verify(mockRsuCredentialRepository).findByNickname(nickname);
        verify(mockRsuCredentialRepository).save(any());
    }

    @Test
    void testUpdate_ChangePassword_Failure_CredentialNotFound() {
        // Arrange
        String nickname = "nickname";
        String organization = "organization";
        String newPassword = "mynewpassword";
        RsuCredentialController.RsuCredentialPatch rsuCredentialPatch = new RsuCredentialController.RsuCredentialPatch(nickname);
        rsuCredentialPatch.setPassword(newPassword);

        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> rsuCredentialManagementService.update(organization, rsuCredentialPatch));
    }

    @Test
    void testUpdate_ChangeOrganization_Failure_OrganizationNotFound() {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        int organizationId = 1;
        String newOrganization = "organization";
        RsuCredentialController.RsuCredentialPatch rsuCredentialPatch = new RsuCredentialController.RsuCredentialPatch(nickname);
        rsuCredentialPatch.setOrganization(newOrganization);

        RsuCredential existingCredential = new RsuCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);
        
        Organization mockOrganization = mock(Organization.class);
        lenient().when(mockOrganization.getId()).thenReturn(organizationId);
        existingCredential.setOwnerOrganization(mockOrganization);

        when(mockOrganizationRepository.findByName(organization)).thenReturn(Optional.of(mockOrganization));

        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));
        when(mockOrganizationRepository.findByName(newOrganization)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> rsuCredentialManagementService.update(organization, rsuCredentialPatch));
    }

    @Test
    void testDeleteByNickname_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String organization = "organization";
        int organizationId = 1;

        RsuCredential existingCredential = new RsuCredential();
        Organization mockOrganization = mock(Organization.class);
        when(mockOrganization.getId()).thenReturn(organizationId);
        existingCredential.setOwnerOrganization(mockOrganization);
        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));

        when(mockOrganizationRepository.findByName(organization)).thenReturn(Optional.of(mockOrganization));

        // Act
        rsuCredentialManagementService.deleteByNickname(organization, nickname);

        // Assert
        verify(mockRsuCredentialRepository).delete(existingCredential);
    }

    @Test
    void testDeleteByNickname_Failure_NotFound() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String organization = "organization";
        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> rsuCredentialManagementService.deleteByNickname(organization, nickname));
    }

    @Test
    void testGetByNickname_Failure_DifferentOrganization() {
        // Arrange
        String nickname = "nickname";
        String requestingOrganization = "org1";
        int requestingOrgId = 1;
        int credentialOrgId = 2; // Different organization

        RsuCredential credential = new RsuCredential();
        credential.setNickname(nickname);
        Organization mockCredentialOrganization = mock(Organization.class);
        when(mockCredentialOrganization.getId()).thenReturn(credentialOrgId);
        credential.setOwnerOrganization(mockCredentialOrganization);
        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(credential));

        Organization mockRequestOrganization = mock(Organization.class);
        when(mockRequestOrganization.getId()).thenReturn(requestingOrgId);
        when(mockOrganizationRepository.findByName(requestingOrganization)).thenReturn(Optional.of(mockRequestOrganization));

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> rsuCredentialManagementService.getByNickname(requestingOrganization, nickname));
    }

    @Test
    void testUpdate_Failure_DifferentOrganization() {
        // Arrange
        String nickname = "nickname";
        String requestingOrganization = "org1";
        int requestingOrgId = 1;
        int credentialOrgId = 2; // Different organization
        String newPassword = "newPassword";

        RsuCredentialController.RsuCredentialPatch patch = new RsuCredentialController.RsuCredentialPatch(nickname);
        patch.setPassword(newPassword);

        RsuCredential existingCredential = new RsuCredential();
        existingCredential.setNickname(nickname);
        Organization mockCredentialOrganization = mock(Organization.class);
        when(mockCredentialOrganization.getId()).thenReturn(credentialOrgId);
        existingCredential.setOwnerOrganization(mockCredentialOrganization);
        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));

        Organization mockRequestOrganization = mock(Organization.class);
        when(mockRequestOrganization.getId()).thenReturn(requestingOrgId);
        when(mockOrganizationRepository.findByName(requestingOrganization)).thenReturn(Optional.of(mockRequestOrganization));

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> rsuCredentialManagementService.update(requestingOrganization, patch));
    }

    @Test
    void testDeleteByNickname_Failure_DifferentOrganization() {
        // Arrange
        String nickname = "nickname";
        String requestingOrganization = "org1";
        int requestingOrgId = 1;
        int credentialOrgId = 2; // Different organization

        RsuCredential existingCredential = new RsuCredential();
        existingCredential.setNickname(nickname);
        Organization mockCredentialOrganization = mock(Organization.class);
        when(mockCredentialOrganization.getId()).thenReturn(credentialOrgId);
        existingCredential.setOwnerOrganization(mockCredentialOrganization);
        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));

        Organization mockRequestOrganization = mock(Organization.class);
        when(mockRequestOrganization.getId()).thenReturn(requestingOrgId);
        when(mockOrganizationRepository.findByName(requestingOrganization)).thenReturn(Optional.of(mockRequestOrganization));

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> rsuCredentialManagementService.deleteByNickname(requestingOrganization, nickname));
    }

}