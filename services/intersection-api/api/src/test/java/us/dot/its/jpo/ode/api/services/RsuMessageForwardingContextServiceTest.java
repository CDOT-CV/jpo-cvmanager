package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpProtocol;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RsuMessageForwardingContextServiceTest {
    private final RsuRepository repository = mock(RsuRepository.class);
    private final RsuMessageForwardingContextService service = new RsuMessageForwardingContextService(repository);

    @Test void loadsActualDatabaseCredentialsAndRejectsMixedProfiles() throws Exception {
        var rsu = new Rsu();
        rsu.setId(7);
        var protocol = new SnmpProtocol();
        protocol.setProtocolCode("1218");
        rsu.setSnmpProtocol(protocol);
        var credentials = new SnmpCredential();
        credentials.setUsername("custom-user");
        credentials.setPassword("fixture-auth-pass");
        credentials.setEncryptPassword("fixture-privacy-pass");
        rsu.setSnmpCredential(credentials);
        when(repository.findByIpv4Address(InetAddress.getByName("192.0.2.10"))).thenReturn(rsu);
        var device = service.load(List.of("192.0.2.10")).getFirst().device();
        assertEquals("custom-user", device.target().credentials().username());
        assertEquals(7, service.load(List.of("192.0.2.10")).getFirst().rsuId());
        var legacy = new Rsu();
        var legacyProtocol = new SnmpProtocol();
        legacyProtocol.setProtocolCode("41");
        legacy.setSnmpProtocol(legacyProtocol);
        when(repository.findByIpv4Address(InetAddress.getByName("192.0.2.11"))).thenReturn(legacy);
        var failure = assertThrows(ResponseStatusException.class,
                () -> service.load(List.of("192.0.2.10", "192.0.2.11")));
        assertEquals(400, failure.getStatusCode().value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "2001:db8::1", "192.0.2", "192.000.2.10", "192.0.2.10 ", "999.0.2.10"})
    void invalidAddressesFailBeforeInventoryLookup(String address) {
        var failure = assertThrows(ResponseStatusException.class, () -> service.load(List.of(address)));
        assertEquals(400, failure.getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test void missingDeviceAndInvalidCredentialsFailBeforeDispatch() throws Exception {
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.load(List.of("192.0.2.10"))).getStatusCode().value());
        var rsu = new Rsu();
        var protocol = new SnmpProtocol();
        protocol.setProtocolCode("1218");
        rsu.setSnmpProtocol(protocol);
        rsu.setSnmpCredential(new SnmpCredential());
        when(repository.findByIpv4Address(InetAddress.getByName("192.0.2.10"))).thenReturn(rsu);
        var failure = assertThrows(ResponseStatusException.class,
                () -> service.load(List.of("192.0.2.10")));
        assertEquals(400, failure.getStatusCode().value());
        assertNull(failure.getCause());
    }
}
