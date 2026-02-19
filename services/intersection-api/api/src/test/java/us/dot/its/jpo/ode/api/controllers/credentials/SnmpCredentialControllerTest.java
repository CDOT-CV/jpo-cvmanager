package us.dot.its.jpo.ode.api.controllers.credentials;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import us.dot.its.jpo.ode.api.mappers.SnmpCredentialMapper;
import us.dot.its.jpo.ode.api.mappers.SnmpCredentialMapperImpl;
import us.dot.its.jpo.ode.api.models.credentials.SnmpCredentialDTO;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;
import us.dot.its.jpo.ode.api.services.RsuCredentialManagementService;
import us.dot.its.jpo.ode.api.services.SnmpCredentialManagementService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnmpCredentialControllerTest {

    SnmpCredentialManagementService mockSnmpCredentialManagementService;

    SnmpCredentialMapper snmpCredentialMapper;

    SnmpCredentialController snmpCredentialController;

    @BeforeEach
    void setUp() {
        mockSnmpCredentialManagementService = mock(SnmpCredentialManagementService.class);
        snmpCredentialMapper = new SnmpCredentialMapperImpl();
    }

    @Test
    void testCreateSnmpCredential_Success() throws SnmpCredentialManagementService.SnmpCredentialAlreadyExistsException, EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        int mockOrganizationId = 1;
        SnmpCredentialController.SnmpCredentialCreateRequest request = new SnmpCredentialController.SnmpCredentialCreateRequest(nickname, username, password, organization);

        SnmpCredential snmpCredential = new SnmpCredential();
        snmpCredential.setNickname(nickname);
        snmpCredential.setUsername(username);
        snmpCredential.setPassword(password);
        snmpCredential.setOwnerOrganizationId(mockOrganizationId);

        SnmpCredentialDTO expected = new SnmpCredentialDTO(null, nickname, username, password, mockOrganizationId);

        when(mockSnmpCredentialManagementService.create(organization, request)).thenReturn(snmpCredential);
        snmpCredentialController = new SnmpCredentialController(mockSnmpCredentialManagementService, snmpCredentialMapper);

        // Act
        SnmpCredentialDTO response = snmpCredentialController.createSnmpCredential(organization, request);

        // Assert
        assertNotNull(response);
        assertEquals(expected, response);
        verify(mockSnmpCredentialManagementService).create(organization, request);
    }

    @Test
    void testCreateSnmpCredential_Failure_AlreadyExists() throws SnmpCredentialManagementService.SnmpCredentialAlreadyExistsException, EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        SnmpCredentialController.SnmpCredentialCreateRequest request = new SnmpCredentialController.SnmpCredentialCreateRequest(nickname, username, password, organization);
        when(mockSnmpCredentialManagementService.create(organization, request)).thenThrow(SnmpCredentialManagementService.SnmpCredentialAlreadyExistsException.class);
        snmpCredentialController = new SnmpCredentialController(mockSnmpCredentialManagementService, snmpCredentialMapper);

        // Act & Assert
        assertThrows(SnmpCredentialManagementService.SnmpCredentialAlreadyExistsException.class, () -> snmpCredentialController.createSnmpCredential(organization, request));
    }

    @Test
    void testCreateSnmpCredential_Failure_OrganizationNotFound() throws SnmpCredentialManagementService.SnmpCredentialAlreadyExistsException, EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        SnmpCredentialController.SnmpCredentialCreateRequest request = new SnmpCredentialController.SnmpCredentialCreateRequest(nickname, username, password, organization);
        when(mockSnmpCredentialManagementService.create(organization, request)).thenThrow(EntityNotFoundException.class);
        snmpCredentialController = new SnmpCredentialController(mockSnmpCredentialManagementService, snmpCredentialMapper);

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> snmpCredentialController.createSnmpCredential(organization, request));
    }

    @Test
    void testGetByNickname_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        int mockRsuCredentialId = 1;
        int mockOrganizationId = 1;

        SnmpCredentialController.SnmpCredentialGetRequest request = new SnmpCredentialController.SnmpCredentialGetRequest(nickname);

        SnmpCredential existingCredential = new SnmpCredential();
        existingCredential.setId(mockRsuCredentialId);
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);
        existingCredential.setOwnerOrganizationId(mockOrganizationId);

        SnmpCredentialDTO expected = new SnmpCredentialDTO(mockRsuCredentialId, nickname, username, password, mockOrganizationId);

        when(mockSnmpCredentialManagementService.getByNickname(organization, nickname)).thenReturn(existingCredential);
        snmpCredentialController = new SnmpCredentialController(mockSnmpCredentialManagementService, snmpCredentialMapper);

        // Act
        SnmpCredentialDTO actual = snmpCredentialController.getByNickname(organization, request);

        // Assert
        assertNotNull(actual);
        assertEquals(expected, actual);
        verify(mockSnmpCredentialManagementService).getByNickname(organization, nickname);
    }

    @Test
    void testGetByNickname_Failure_NotFound() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String organization = "organization";
        SnmpCredentialController.SnmpCredentialGetRequest request = new SnmpCredentialController.SnmpCredentialGetRequest(nickname);
        when(mockSnmpCredentialManagementService.getByNickname(organization, nickname)).thenThrow(EntityNotFoundException.class);
        snmpCredentialController = new SnmpCredentialController(mockSnmpCredentialManagementService, snmpCredentialMapper);

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> snmpCredentialController.getByNickname(organization, request));
    }

    @Test
    void testUpdate_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String updatedPassword = "updatedPassword";
        String organization = "organization";
        int mockRsuCredentialId = 1;
        int mockOrganizationId = 2;
       SnmpCredential snmpCredential = new SnmpCredential();
       snmpCredential.setId(mockRsuCredentialId);
       snmpCredential.setNickname(nickname);
       snmpCredential.setUsername(username);
       snmpCredential.setPassword(updatedPassword);
       snmpCredential.setOwnerOrganizationId(mockOrganizationId);

       SnmpCredentialController.SnmpCredentialPatch patch = new SnmpCredentialController.SnmpCredentialPatch(nickname);
       patch.setPassword(updatedPassword);

       when(mockSnmpCredentialManagementService.update(organization, patch)).thenReturn(snmpCredential);
       snmpCredentialController = new SnmpCredentialController(mockSnmpCredentialManagementService, snmpCredentialMapper);

       SnmpCredentialDTO expected = new SnmpCredentialDTO(mockRsuCredentialId, nickname, username, updatedPassword, mockOrganizationId);

       // Act
       SnmpCredentialDTO response = snmpCredentialController.update(organization, patch);

       // Assert
       assertNotNull(response);
       assertEquals(expected, response);
       verify(mockSnmpCredentialManagementService).update(organization, patch);
    }

    @Test
    void testUpdate_Failure_EntityNotFound() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String organization = "organization";
        SnmpCredentialController.SnmpCredentialPatch patch = new SnmpCredentialController.SnmpCredentialPatch(nickname);
        patch.setPassword("");
        when(mockSnmpCredentialManagementService.update(organization, patch)).thenThrow(EntityNotFoundException.class);
        snmpCredentialController = new SnmpCredentialController(mockSnmpCredentialManagementService, snmpCredentialMapper);

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> snmpCredentialController.update(organization, patch));
    }

    @Test
    void testDeleteByNickname_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String organization = "organization";

        SnmpCredentialController.SnmpCredentialDeleteRequest request = new SnmpCredentialController.SnmpCredentialDeleteRequest(nickname);

        doNothing().when(mockSnmpCredentialManagementService).deleteByNickname(organization, nickname);
        snmpCredentialController = new SnmpCredentialController(mockSnmpCredentialManagementService, snmpCredentialMapper);

        // Act
        snmpCredentialController.deleteByNickname(organization, request);

        // Assert
        verify(mockSnmpCredentialManagementService).deleteByNickname(organization, nickname);
    }

    @Test
    void testDelete_Failure() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String organization = "organization";
        doThrow(EntityNotFoundException.class).when(mockSnmpCredentialManagementService).deleteByNickname(organization, nickname);
        snmpCredentialController = new SnmpCredentialController(mockSnmpCredentialManagementService, snmpCredentialMapper);
        SnmpCredentialController.SnmpCredentialDeleteRequest request = new SnmpCredentialController.SnmpCredentialDeleteRequest(nickname);

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> snmpCredentialController.deleteByNickname(organization, request));

    }

}