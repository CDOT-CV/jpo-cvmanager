package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.Test;
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

    @Test
    void testCreate_Success() {
        // TODO: implement
    }

    // TODO: implement tests for unhappy paths

    @Test
    void testGetByNickname_Success() {
        // TODO: implement
    }

    // TODO: implement tests for unhappy paths

    @Test
    void testUpdate_Success() {
        // TODO: implement
    }

    // TODO: implement tests for unhappy paths

    @Test
    void testDeleteByNickname_Success() {
        // TODO: implement
    }

    // TODO: implement tests for unhappy paths

}