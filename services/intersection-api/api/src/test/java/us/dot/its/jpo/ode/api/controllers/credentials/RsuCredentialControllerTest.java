package us.dot.its.jpo.ode.api.controllers.credentials;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import us.dot.its.jpo.ode.api.mappers.RsuCredentialMapper;
import us.dot.its.jpo.ode.api.mappers.RsuCredentialMapperImpl;
import us.dot.its.jpo.ode.api.models.credentials.RsuCredentialDTO;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.services.RsuCredentialManagementService;

import java.util.Optional;

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
    void testCreateRsuCredential_Success() throws RsuCredentialManagementService.RsuCredentialAlreadyExistsException, RsuCredentialManagementService.OrganizationNotFoundException {
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
        when(mockRsuCredentialManagementService.create(rsuCredentialCreateRequest)).thenReturn(mockRsuCredential);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        RsuCredentialDTO expected = new RsuCredentialDTO(mockRsuCredentialId, nickname, username, password, mockOrganizationId);

        // Act
        RsuCredentialController.RsuCredentialCreateResponse response = rsuCredentialController.createRsuCredential(rsuCredentialCreateRequest);

        // Assert
        assert(response != null);
        assert(response.getSuccess());
        assert(response.getRsuCredential().isPresent());
        assert(response.getError().isEmpty());
        assert(response.getRsuCredential().get().equals(expected));
        verify(mockRsuCredentialManagementService).create(rsuCredentialCreateRequest);
    }

    @Test
    void testCreateRsuCredential_Failure_AlreadyExists() throws RsuCredentialManagementService.OrganizationNotFoundException, RsuCredentialManagementService.RsuCredentialAlreadyExistsException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        RsuCredentialController.RsuCredentialCreateRequest rsuCredentialCreateRequest = new RsuCredentialController.RsuCredentialCreateRequest(nickname, username, password, organization);
        when(mockRsuCredentialManagementService.create(rsuCredentialCreateRequest)).thenThrow(RsuCredentialManagementService.RsuCredentialAlreadyExistsException.class);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        // Act
        RsuCredentialController.RsuCredentialCreateResponse response = rsuCredentialController.createRsuCredential(rsuCredentialCreateRequest);

        // Assert
        assert(response != null);
        assert(!response.getSuccess());
        assert(response.getError().isPresent());
        assert(response.getError().get().equals("RSU Credential already exists"));
    }

    @Test
    void testCreateRsuCredential_Failure_OrganizationNotFound() throws RsuCredentialManagementService.OrganizationNotFoundException, RsuCredentialManagementService.RsuCredentialAlreadyExistsException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        RsuCredentialController.RsuCredentialCreateRequest rsuCredentialCreateRequest = new RsuCredentialController.RsuCredentialCreateRequest(nickname, username, password, organization);
        doThrow(RsuCredentialManagementService.OrganizationNotFoundException.class).when(mockRsuCredentialManagementService).create(rsuCredentialCreateRequest);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        // Act
        RsuCredentialController.RsuCredentialCreateResponse response = rsuCredentialController.createRsuCredential(rsuCredentialCreateRequest);

        // Assert
        assert(response != null);
        assert(!response.getSuccess());
        assert(response.getError().isPresent());
        assert(response.getError().get().equals("Organization not found"));
    }

    @Test
    void testGetByNickname_Success() throws RsuCredentialManagementService.RsuCredentialNotFoundException {
        // Arrange
        String nickname = "testNickname";
        String username = "testUser";
        String password = "testPassword";
        int mockRsuCredentialId = 1;
        int mockOrganizationId = 2;
        RsuCredential mockRsuCredential = mock();
        when(mockRsuCredential.getId()).thenReturn(mockRsuCredentialId);
        when(mockRsuCredential.getNickname()).thenReturn(nickname);
        when(mockRsuCredential.getUsername()).thenReturn(username);
        when(mockRsuCredential.getPassword()).thenReturn(password);
        when(mockRsuCredential.getOwnerOrganizationId()).thenReturn(mockOrganizationId);
        when(mockRsuCredentialManagementService.getByNickname(nickname)).thenReturn(mockRsuCredential);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        RsuCredentialController.RsuCredentialGetRequest rsuCredentialGetRequest = new RsuCredentialController.RsuCredentialGetRequest(nickname);
        RsuCredentialDTO expected = new RsuCredentialDTO(mockRsuCredentialId, nickname, username, password, mockOrganizationId);

        // Act
        Optional<RsuCredentialDTO> actual = rsuCredentialController.getByNickname(rsuCredentialGetRequest);

        // Assert
        assert(actual.isPresent());
        assert(actual.get().equals(expected));
        verify(mockRsuCredentialManagementService).getByNickname(nickname);
    }

    @Test
    void testGetByNickname_Failure_NotFound() throws RsuCredentialManagementService.RsuCredentialNotFoundException {
        // Arrange
        String nickname = "nickname";
        when(mockRsuCredentialManagementService.getByNickname(nickname)).thenThrow(RsuCredentialManagementService.RsuCredentialNotFoundException.class);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);
        RsuCredentialController.RsuCredentialGetRequest rsuCredentialGetRequest = new RsuCredentialController.RsuCredentialGetRequest(nickname);

        // Act
        Optional<RsuCredentialDTO> actual = rsuCredentialController.getByNickname(rsuCredentialGetRequest);

        // Assert
        assert(!actual.isPresent());
    }

    @Test
    void testUpdate_Success() throws RsuCredentialManagementService.RsuCredentialNotFoundException, RsuCredentialManagementService.OrganizationNotFoundException {
        // Arrange
        String nickname = "testNickname";
        String username = "testUser";
        String updatedPassword = "updatedPassword";
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

        when(mockRsuCredentialManagementService.update(rsuCredentialPatch)).thenReturn(mockUpdatedRsuCredential);

        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        // Act
        RsuCredentialController.RsuCredentialUpdateResponse response = rsuCredentialController.update(rsuCredentialPatch);

        // Assert
        assert(response != null);
        assert(response.getUpdatedRsuCredential().isPresent());
        assert(response.getUpdatedRsuCredential().get().getPassword().equals(updatedPassword));
        assert(response.getError().isEmpty());
        verify(mockRsuCredentialManagementService).update(rsuCredentialPatch);
    }

    @Test
    void testUpdate_Failure_CredentialNotFound() throws RsuCredentialManagementService.RsuCredentialNotFoundException, RsuCredentialManagementService.OrganizationNotFoundException {
        // Arrange
        String nickname = "nickname";
        RsuCredentialController.RsuCredentialPatch rsuCredentialPatch = new RsuCredentialController.RsuCredentialPatch(nickname);
        rsuCredentialPatch.setPassword("");
        when(mockRsuCredentialManagementService.update(rsuCredentialPatch)).thenThrow(RsuCredentialManagementService.RsuCredentialNotFoundException.class);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        // Act
        RsuCredentialController.RsuCredentialUpdateResponse response = rsuCredentialController.update(rsuCredentialPatch);

        // Assert
        assert(response != null);
        assert(!response.getUpdatedRsuCredential().isPresent());
        assert(response.getError().isPresent());
        assert(response.getError().get().equals("RSU Credential not found"));
    }

    @Test
    void testUpdate_Failure_OrganizationNotFound() throws RsuCredentialManagementService.OrganizationNotFoundException, RsuCredentialManagementService.RsuCredentialNotFoundException {
        // Arrange
        String nickname = "nickname";
        RsuCredentialController.RsuCredentialPatch rsuCredentialPatch = new RsuCredentialController.RsuCredentialPatch(nickname);
        when(mockRsuCredentialManagementService.update(rsuCredentialPatch)).thenThrow(RsuCredentialManagementService.OrganizationNotFoundException.class);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        // Act
        RsuCredentialController.RsuCredentialUpdateResponse response = rsuCredentialController.update(rsuCredentialPatch);

        // Assert
        assert(response != null);
        assert(!response.getUpdatedRsuCredential().isPresent());
        assert(response.getError().isPresent());
        assert(response.getError().get().equals("Organization not found"));
    }

    @Test
    void testDelete_Success() {
        // Arrange
        String nickname = "testNickname";

        when(mockRsuCredentialManagementService.deleteByNickname(nickname)).thenReturn(true);

        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        RsuCredentialController.RsuCredentialDeleteRequest deleteRequest = new RsuCredentialController.RsuCredentialDeleteRequest(nickname);

        // Act
        RsuCredentialController.RsuCredentialDeleteResponse response = rsuCredentialController.deleteByNickname(deleteRequest);

        // Assert
        assert(response != null);
        assert(response.getSuccess());
        assert(response.getError().isEmpty());
        verify(mockRsuCredentialManagementService).deleteByNickname(nickname);
    }

    @Test
    void testDelete_Failure() {
        // Arrange
        String nickname = "nickname";
        when(mockRsuCredentialManagementService.deleteByNickname(nickname)).thenReturn(false);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        // Act
        RsuCredentialController.RsuCredentialDeleteResponse response = rsuCredentialController.deleteByNickname(new RsuCredentialController.RsuCredentialDeleteRequest(nickname));

        // Assert
        assert(response != null);
        assert(!response.getSuccess());
        assert(response.getError().isPresent());
        assert(response.getError().get().equals("RSU Credential not found"));
    }

}