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

import static org.mockito.Mockito.mock;
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
    void testCreateRsuCredential_Success() {
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
        when(mockRsuCredentialManagementService.createRsuCredential(nickname, username, password, organization)).thenReturn(mockRsuCredential);
        rsuCredentialController = new RsuCredentialController(mockRsuCredentialManagementService, rsuCredentialMapper);

        RsuCredentialDTO expected = new RsuCredentialDTO(mockRsuCredentialId, nickname, username, password, mockOrganizationId);

        // Act
        RsuCredentialDTO actual = rsuCredentialController.createRsuCredential(nickname, username, password, organization);

        // Assert
        assert(actual != null);
        assert(actual.equals(expected));
    }

    // TODO: implement tests

}