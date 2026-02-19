package us.dot.its.jpo.ode.api.controllers.credentials;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import us.dot.its.jpo.ode.api.mappers.RsuCredentialMapper;
import us.dot.its.jpo.ode.api.mappers.RsuCredentialMapperImpl;
import us.dot.its.jpo.ode.api.models.credentials.RsuCredentialDTO;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.services.RsuCredentialManagementService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RsuCredentialControllerTest {

    RsuCredentialManagementService mockRsuCredentialManagementService;

    RsuCredentialMapper rsuCredentialMapper;

    RsuCredentialController rsuCredentialController;

    @BeforeEach()
    void setUp() {
        mockRsuCredentialManagementService = mock(RsuCredentialManagementService.class);
        rsuCredentialMapper = new RsuCredentialMapperImpl();
    }

    @Test
    void testCreateRsuCredential_Success() throws RsuCredentialManagementService.RsuCredentialAlreadyExistsException, EntityNotFoundException {
        // Arrange
        String nickname = "testNickname";
        String username = "testUser";
        String password = "testPassword";
        String organization = "testOrg";
        int mockRsuCredentialId = 1;
        int mockOrganizationId = 2;
        RsuCredential mockRsuCredential = mock();
        when(mockRsuCredential.getId()).thenReturn(mockRsuCredentialId);
        when(mockRsuCredential.getNickname()).thenReturn(nickname);
        when(mockRsuCredential.getUsername()).thenReturn(username);
        when(mockRsuCredential.getPassword()).thenReturn(password);
        when(mockRsuCredential.getOwnerOrganizationId()).thenReturn(mockOrganizationId);
        RsuCredentialController.RsuCredentialCreateRequest rsuCredentialCreateRequest = new RsuCredentialController.RsuCredentialCreateRequest(nickname, username, password, organization);
        when(mockRsuCredentialManagementService.create(organization, rsuCredentialCreateRequest)).thenReturn(mockRsuCredential);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        RsuCredentialDTO expected = new RsuCredentialDTO(mockRsuCredentialId, nickname, username, password, mockOrganizationId);

        // Act
        RsuCredentialDTO response = rsuCredentialController.createRsuCredential(organization, rsuCredentialCreateRequest);

        // Assert
        assert(response != null);
        assert(response.equals(expected));
        verify(mockRsuCredentialManagementService).create(organization, rsuCredentialCreateRequest);
    }

    @Test
    void testCreateRsuCredential_Failure_AlreadyExists() throws EntityNotFoundException, RsuCredentialManagementService.RsuCredentialAlreadyExistsException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        RsuCredentialController.RsuCredentialCreateRequest rsuCredentialCreateRequest = new RsuCredentialController.RsuCredentialCreateRequest(nickname, username, password, organization);
        when(mockRsuCredentialManagementService.create(organization, rsuCredentialCreateRequest)).thenThrow(RsuCredentialManagementService.RsuCredentialAlreadyExistsException.class);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        // Act & Assert
        assertThrows(RsuCredentialManagementService.RsuCredentialAlreadyExistsException.class, () -> rsuCredentialController.createRsuCredential(organization, rsuCredentialCreateRequest));
    }

    @Test
    void testCreateRsuCredential_Failure_OrganizationNotFound() throws EntityNotFoundException, RsuCredentialManagementService.RsuCredentialAlreadyExistsException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        RsuCredentialController.RsuCredentialCreateRequest rsuCredentialCreateRequest = new RsuCredentialController.RsuCredentialCreateRequest(nickname, username, password, organization);
        doThrow(EntityNotFoundException.class).when(mockRsuCredentialManagementService).create(organization, rsuCredentialCreateRequest);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> rsuCredentialController.createRsuCredential(organization, rsuCredentialCreateRequest));
    }

    @Test
    void testGetByNickname_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "testNickname";
        String username = "testUser";
        String password = "testPassword";
        String organization = "testOrg";
        int mockRsuCredentialId = 1;
        int mockOrganizationId = 2;
        RsuCredential mockRsuCredential = mock();
        when(mockRsuCredential.getId()).thenReturn(mockRsuCredentialId);
        when(mockRsuCredential.getNickname()).thenReturn(nickname);
        when(mockRsuCredential.getUsername()).thenReturn(username);
        when(mockRsuCredential.getPassword()).thenReturn(password);
        when(mockRsuCredential.getOwnerOrganizationId()).thenReturn(mockOrganizationId);
        when(mockRsuCredentialManagementService.getByNickname(organization, nickname)).thenReturn(mockRsuCredential);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        RsuCredentialController.RsuCredentialGetRequest rsuCredentialGetRequest = new RsuCredentialController.RsuCredentialGetRequest(nickname);
        RsuCredentialDTO expected = new RsuCredentialDTO(mockRsuCredentialId, nickname, username, password, mockOrganizationId);

        // Act
        RsuCredentialDTO actual = rsuCredentialController.getByNickname(organization, rsuCredentialGetRequest);

        // Assert
        assert(actual != null);
        assert(actual.equals(expected));
        verify(mockRsuCredentialManagementService).getByNickname(organization, nickname);
    }

    @Test
    void testGetByNickname_Failure_NotFound() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String organization = "testOrg";
        when(mockRsuCredentialManagementService.getByNickname(organization, nickname)).thenThrow(EntityNotFoundException.class);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);
        RsuCredentialController.RsuCredentialGetRequest rsuCredentialGetRequest = new RsuCredentialController.RsuCredentialGetRequest(nickname);

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> rsuCredentialController.getByNickname(organization, rsuCredentialGetRequest));
    }

    @Test
    void testUpdate_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "testNickname";
        String username = "testUser";
        String updatedPassword = "updatedPassword";
        String organization = "testOrg";
        int mockRsuCredentialId = 1;
        int mockOrganizationId = 2;
        RsuCredential mockUpdatedRsuCredential = mock();
        when(mockUpdatedRsuCredential.getId()).thenReturn(mockRsuCredentialId);
        when(mockUpdatedRsuCredential.getNickname()).thenReturn(nickname);
        when(mockUpdatedRsuCredential.getUsername()).thenReturn(username);
        when(mockUpdatedRsuCredential.getOwnerOrganizationId()).thenReturn(mockOrganizationId);
        when(mockUpdatedRsuCredential.getPassword()).thenReturn(updatedPassword);

        RsuCredentialController.RsuCredentialPatch rsuCredentialPatch = new RsuCredentialController.RsuCredentialPatch(nickname);
        rsuCredentialPatch.setPassword(updatedPassword);

        when(mockRsuCredentialManagementService.update(organization, rsuCredentialPatch)).thenReturn(mockUpdatedRsuCredential);

        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        // Act
        RsuCredentialDTO response = rsuCredentialController.update(organization, rsuCredentialPatch);

        // Assert
        assert(response != null);
        assert(response.getId().equals(mockRsuCredentialId));
        verify(mockRsuCredentialManagementService).update(organization, rsuCredentialPatch);
    }

    @Test
    void testUpdate_Failure_CredentialNotFound() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String organization = "testOrg";
        RsuCredentialController.RsuCredentialPatch rsuCredentialPatch = new RsuCredentialController.RsuCredentialPatch(nickname);
        rsuCredentialPatch.setPassword("");
        when(mockRsuCredentialManagementService.update(organization, rsuCredentialPatch)).thenThrow(EntityNotFoundException.class);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> rsuCredentialController.update(organization, rsuCredentialPatch));
    }

    @Test
    void testUpdate_Failure_OrganizationNotFound() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String organization = "testOrg";
        RsuCredentialController.RsuCredentialPatch rsuCredentialPatch = new RsuCredentialController.RsuCredentialPatch(nickname);
        when(mockRsuCredentialManagementService.update(organization, rsuCredentialPatch)).thenThrow(EntityNotFoundException.class);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> rsuCredentialController.update(organization, rsuCredentialPatch));
    }

    @Test
    void testDelete_Success() {
        // Arrange
        String nickname = "testNickname";
        String organization = "testOrg";

        doNothing().when(mockRsuCredentialManagementService).deleteByNickname(organization, nickname);

        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        RsuCredentialController.RsuCredentialDeleteRequest deleteRequest = new RsuCredentialController.RsuCredentialDeleteRequest(nickname);

        // Act
        rsuCredentialController.deleteByNickname(organization, deleteRequest);

        // Assert
        verify(mockRsuCredentialManagementService).deleteByNickname(organization, nickname);
    }

    @Test
    void testDelete_Failure() {
        // Arrange
        String nickname = "nickname";
        String organization = "testOrg";
        doThrow(EntityNotFoundException.class).when(mockRsuCredentialManagementService).deleteByNickname(organization, nickname);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);
        RsuCredentialController.RsuCredentialDeleteRequest deleteRequest = new RsuCredentialController.RsuCredentialDeleteRequest(nickname);

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> rsuCredentialController.deleteByNickname(organization, deleteRequest));
    }

}