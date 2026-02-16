package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import us.dot.its.jpo.ode.api.repositories.SnmpCredentialRepository;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SnmpCredentialManagementServiceTest {

    @Mock
    SnmpCredentialRepository mockSnmpCredentialRepository;

    @InjectMocks
    SnmpCredentialManagementService snmpCredentialManagementService;

    // TODO: implement tests

}