package us.dot.its.jpo.ode.api.controllers.credentials;

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

    // TODO: implement tests
}