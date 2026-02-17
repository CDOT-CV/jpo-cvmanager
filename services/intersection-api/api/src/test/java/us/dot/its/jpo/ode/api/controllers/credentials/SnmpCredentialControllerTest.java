package us.dot.its.jpo.ode.api.controllers.credentials;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import us.dot.its.jpo.ode.api.mappers.SnmpCredentialMapper;
import us.dot.its.jpo.ode.api.mappers.SnmpCredentialMapperImpl;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;
import us.dot.its.jpo.ode.api.services.SnmpCredentialManagementService;

import static org.junit.jupiter.api.Assertions.*;
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
    void testCreateSnmpCredential_Success() throws SnmpCredentialManagementService.SnmpCredentialAlreadyExistsException, SnmpCredentialManagementService.OrganizationNotFoundException {
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

        when(mockSnmpCredentialManagementService.create(request)).thenReturn(snmpCredential);
        snmpCredentialController = new SnmpCredentialController(mockSnmpCredentialManagementService, snmpCredentialMapper);

        // Act
        SnmpCredentialController.SnmpCredentialCreateResponse response = snmpCredentialController.createSnmpCredential(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertNotNull(response.getSnmpCredential());
        verify(mockSnmpCredentialManagementService).create(request);
    }

    // TODO: implement unit tests for unhappy paths

    @Test
    void testGetByNickname_Success() {
        // TODO: implement
    }

    // TODO: implement unit tests for unhappy paths

    @Test
    void testUpdate_Success() {
        // TODO: implement
    }

    // TODO: implement unit tests for unhappy paths

    @Test
    void testDeleteByNickname_Success() {
        // TODO: implement
    }

    // TODO: implement unit tests for unhappy paths

}