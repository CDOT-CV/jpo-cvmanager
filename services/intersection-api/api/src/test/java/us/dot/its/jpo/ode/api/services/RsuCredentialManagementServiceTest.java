package us.dot.its.jpo.ode.api.services;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import us.dot.its.jpo.ode.api.controllers.credentials.RsuCredentialController;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuCredentialRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
        mockRsuCredential.setOwnerOrganizationId(ownerOrganizationId);
        when(mockRsuCredentialRepository.save(any())).thenReturn(mockRsuCredential);

        // Act
        RsuCredential rsuCredential = rsuCredentialManagementService.create(request);

        // Assert
        assertNotNull(rsuCredential);
        assertEquals(nickname, rsuCredential.getNickname());
        assertEquals(username, rsuCredential.getUsername());
        assertEquals(password, rsuCredential.getPassword());
        assertEquals(1, rsuCredential.getOwnerOrganizationId());
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
        assertThrows(RsuCredentialManagementService.RsuCredentialAlreadyExistsException.class, () -> rsuCredentialManagementService.create(request));
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
        assertThrows(EntityNotFoundException.class, () -> rsuCredentialManagementService.create(request));
    }

    @Test
    void testGetByNickname_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        RsuCredential mockRsuCredential = new RsuCredential();
        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(mockRsuCredential));

        // Act
        RsuCredential rsuCredential = rsuCredentialManagementService.getByNickname(nickname);

        // Assert
        assertNotNull(rsuCredential);
        assertEquals(mockRsuCredential, rsuCredential);
        verify(mockRsuCredentialRepository).findByNickname(nickname);
    }

    @Test
    void testGetByNickname_Failure_NotFound() {
        // Arrange
        String nickname = "nickname";
        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> rsuCredentialManagementService.getByNickname(nickname));
    }

    @Test
    void testUpdate_ChangePassword_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        int organizationId = 1;
        String newPassword = "mynewpassword";
        RsuCredentialController.RsuCredentialPatch rsuCredentialPatch = new RsuCredentialController.RsuCredentialPatch(nickname);
        rsuCredentialPatch.setPassword(newPassword);

        RsuCredential existingCredential = new RsuCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);
        existingCredential.setOwnerOrganizationId(organizationId);

        RsuCredential expectedCredential = new RsuCredential();
        expectedCredential.setNickname(nickname);
        expectedCredential.setUsername(username);
        expectedCredential.setPassword(newPassword);
        expectedCredential.setOwnerOrganizationId(organizationId);

        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));
        when(mockRsuCredentialRepository.save(any())).thenReturn(expectedCredential);

        // Act
        RsuCredential updatedCredential = rsuCredentialManagementService.update(rsuCredentialPatch);

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
        int organizationId = 1;
        String newOrganization = "neworganization";
        int newOrganizationId = 2;
        RsuCredentialController.RsuCredentialPatch rsuCredentialPatch = new RsuCredentialController.RsuCredentialPatch(nickname);
        rsuCredentialPatch.setOrganization(newOrganization);

        Organization mockOrganization = mock(Organization.class);
        when(mockOrganization.getId()).thenReturn(newOrganizationId);
        when(mockOrganizationRepository.findByName(newOrganization)).thenReturn(Optional.of(mockOrganization));

        RsuCredential existingCredential = new RsuCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);
        existingCredential.setOwnerOrganizationId(organizationId);

        RsuCredential expectedCredential = new RsuCredential();
        expectedCredential.setNickname(nickname);
        expectedCredential.setUsername(username);
        expectedCredential.setPassword(password);
        expectedCredential.setOwnerOrganizationId(newOrganizationId);

        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));
        when(mockRsuCredentialRepository.save(any())).thenReturn(expectedCredential);

       // Act
       RsuCredential updatedCredential = rsuCredentialManagementService.update(rsuCredentialPatch);

       // Assert
       assertNotNull(updatedCredential);
       assertEquals(nickname, updatedCredential.getNickname());
       assertEquals(username, updatedCredential.getUsername());
       assertEquals(password, updatedCredential.getPassword());
       assertEquals(newOrganizationId, updatedCredential.getOwnerOrganizationId());
       verify(mockRsuCredentialRepository).findByNickname(nickname);
       verify(mockRsuCredentialRepository).save(any());
    }

    @Test
    void testUpdate_ChangeUsername_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        int organizationId = 1;
        String newUsername = "newUsername";
        RsuCredentialController.RsuCredentialPatch rsuCredentialPatch = new RsuCredentialController.RsuCredentialPatch(nickname);
        rsuCredentialPatch.setUsername(newUsername);

        RsuCredential existingCredential = new RsuCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);
        existingCredential.setOwnerOrganizationId(organizationId);

        RsuCredential expectedCredential = new RsuCredential();
        expectedCredential.setNickname(nickname);
        expectedCredential.setUsername(newUsername);
        expectedCredential.setPassword(password);
        expectedCredential.setOwnerOrganizationId(organizationId);

        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));

        when(mockRsuCredentialRepository.save(any())).thenReturn(expectedCredential);

        // Act
        RsuCredential updatedCredential = rsuCredentialManagementService.update(rsuCredentialPatch);

        // Assert
        assertNotNull(updatedCredential);
        assertEquals(nickname, updatedCredential.getNickname());
        assertEquals(newUsername, updatedCredential.getUsername());
        assertEquals(password, updatedCredential.getPassword());
        assertEquals(organizationId, updatedCredential.getOwnerOrganizationId());
        verify(mockRsuCredentialRepository).findByNickname(nickname);
        verify(mockRsuCredentialRepository).save(any());
    }

    @Test
    void testUpdate_ChangePassword_Failure_CredentialNotFound() {
        // Arrange
        String nickname = "nickname";
        String newPassword = "mynewpassword";
        RsuCredentialController.RsuCredentialPatch rsuCredentialPatch = new RsuCredentialController.RsuCredentialPatch(nickname);
        rsuCredentialPatch.setPassword(newPassword);

        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> rsuCredentialManagementService.update(rsuCredentialPatch));
    }

    @Test
    void testUpdate_ChangeOrganization_Failure_OrganizationNotFound() {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        int organizationId = 1;
        String newOrganization = "newOrganization";
        RsuCredentialController.RsuCredentialPatch rsuCredentialPatch = new RsuCredentialController.RsuCredentialPatch(nickname);
        rsuCredentialPatch.setOrganization(newOrganization);

        RsuCredential existingCredential = new RsuCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);
        existingCredential.setOwnerOrganizationId(organizationId);

        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));
        when(mockOrganizationRepository.findByName(newOrganization)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> rsuCredentialManagementService.update(rsuCredentialPatch));
    }

    @Test
    void testDeleteByNickname_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";

        RsuCredential existingCredential = new RsuCredential();
        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));

        // Act
        rsuCredentialManagementService.deleteByNickname(nickname);

        // Assert
        verify(mockRsuCredentialRepository).delete(existingCredential);
    }

    @Test
    void testDeleteByNickname_Failure_NotFound() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> rsuCredentialManagementService.deleteByNickname(nickname));
    }

}