package us.dot.its.jpo.ode.api.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuOrganization;
import us.dot.its.jpo.ode.api.repositories.RsuOrganizationRepository;

@ExtendWith(MockitoExtension.class)
class RsuUpgradeContextServiceTest {

    @Mock
    private RsuOrganizationRepository rsuOrganizationRepository;

    @InjectMocks
    private RsuUpgradeContextService rsuUpgradeContextService;

    @Test
    void testHasCompleteRsuData_TrueWhenRsuExists() throws UnknownHostException {
        String rsuIp = "10.0.0.10";
        String organization = "TestOrg";
        InetAddress inetAddress = InetAddress.getByName(rsuIp);

        Rsu rsu = new Rsu();
        rsu.setIpv4Address(inetAddress);

        Organization org = new Organization();
        org.setName(organization);

        RsuOrganization rsuOrganization = new RsuOrganization();
        rsuOrganization.setRsu(rsu);
        rsuOrganization.setOrganization(org);

        when(rsuOrganizationRepository.findByRsuIpv4AddressAndOrganization_Name(inetAddress, organization))
                .thenReturn(Optional.of(rsuOrganization));

        boolean result = rsuUpgradeContextService.hasCompleteRsuData(rsuIp, organization);

        assertTrue(result);
        verify(rsuOrganizationRepository).findByRsuIpv4AddressAndOrganization_Name(inetAddress, organization);
    }

    @Test
    void testHasCompleteRsuData_FalseWhenRsuMissing() throws UnknownHostException {
        String rsuIp = "10.0.0.10";
        String organization = "TestOrg";
        InetAddress inetAddress = InetAddress.getByName(rsuIp);

        when(rsuOrganizationRepository.findByRsuIpv4AddressAndOrganization_Name(inetAddress, organization))
                .thenReturn(Optional.empty());

        boolean result = rsuUpgradeContextService.hasCompleteRsuData(rsuIp, organization);

        assertFalse(result);
        verify(rsuOrganizationRepository).findByRsuIpv4AddressAndOrganization_Name(inetAddress, organization);
    }

    @Test
    void testFindRsuForOrganization_ReturnsNullWhenMissing() throws UnknownHostException {
        String rsuIp = "10.0.0.11";
        String organization = "TestOrg";
        InetAddress inetAddress = InetAddress.getByName(rsuIp);

        when(rsuOrganizationRepository.findByRsuIpv4AddressAndOrganization_Name(inetAddress, organization))
                .thenReturn(Optional.empty());

        Rsu result = rsuUpgradeContextService.findRsuForOrganization(rsuIp, organization);

        assertEquals(null, result);
        assertFalse(rsuUpgradeContextService.hasCompleteRsuData(rsuIp, organization));
    }

    @Test
    void testFindRsuForOrganization_Success() throws UnknownHostException {
        String rsuIp = "10.0.0.12";
        String organization = "TestOrg";
        InetAddress inetAddress = InetAddress.getByName(rsuIp);

        Rsu rsu = new Rsu();
        rsu.setIpv4Address(inetAddress);

        RsuOrganization rsuOrganization = new RsuOrganization();
        rsuOrganization.setRsu(rsu);

        when(rsuOrganizationRepository.findByRsuIpv4AddressAndOrganization_Name(inetAddress, organization))
                .thenReturn(Optional.of(rsuOrganization));

        Rsu result = rsuUpgradeContextService.findRsuForOrganization(rsuIp, organization);

        assertSame(rsu, result);
    }

    @Test
    void testFindRsuForOrganization_InvalidIpAddress() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> rsuUpgradeContextService.findRsuForOrganization("invalid-ip", "TestOrg"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Invalid RSU IP address"));
    }
}
