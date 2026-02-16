package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import us.dot.its.jpo.ode.api.repositories.RsuCredentialRepository;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RsuCredentialManagementServiceTest {

    @Mock
    RsuCredentialRepository mockRsuCredentialRepository;

    @InjectMocks
    RsuCredentialManagementService rsuCredentialManagementService;

    // TODO: implement tests
}