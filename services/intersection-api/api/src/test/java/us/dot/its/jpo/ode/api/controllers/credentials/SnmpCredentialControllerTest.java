package us.dot.its.jpo.ode.api.controllers.credentials;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import us.dot.its.jpo.ode.api.services.SnmpCredentialManagementService;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SnmpCredentialControllerTest {

    @Mock
    SnmpCredentialManagementService mockSnmpCredentialManagementService;

    @InjectMocks
    SnmpCredentialController snmpCredentialController;

    @Test
    void testCreateSnmpCredential_Success() {
        // TODO: implement
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