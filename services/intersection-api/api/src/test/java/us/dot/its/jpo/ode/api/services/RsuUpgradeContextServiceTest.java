package us.dot.its.jpo.ode.api.services;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

@ExtendWith(MockitoExtension.class)
class RsuUpgradeContextServiceTest {

    @Mock
    private RsuRepository rsuRepository;

    @InjectMocks
    private RsuUpgradeContextService rsuUpgradeContextService;

    @Test
    void testHasCompleteRsuData_TrueWhenRsuExists() throws UnknownHostException {
        String rsuIp = "10.0.0.10";
        Inet4Address Inet4Address = RsuManagementService.toIpv4Address(rsuIp);

        Rsu rsu = new Rsu();
        rsu.setIpv4Address(Inet4Address);

        when(rsuRepository.findByIpv4Address(Inet4Address)).thenReturn(rsu);

        boolean result = rsuUpgradeContextService.hasCompleteRsuData(rsuIp);

        assertTrue(result);
        verify(rsuRepository).findByIpv4Address(Inet4Address);
    }

    @Test
    void testHasCompleteRsuData_FalseWhenRsuMissing() throws UnknownHostException {
        String rsuIp = "10.0.0.10";
        Inet4Address Inet4Address = RsuManagementService.toIpv4Address(rsuIp);

        when(rsuRepository.findByIpv4Address(Inet4Address)).thenReturn(null);

        boolean result = rsuUpgradeContextService.hasCompleteRsuData(rsuIp);

        assertFalse(result);
        verify(rsuRepository).findByIpv4Address(Inet4Address);
    }

    @Test
    void testFindRsuByIp_ReturnsNullWhenMissing() throws UnknownHostException {
        String rsuIp = "10.0.0.11";
        Inet4Address Inet4Address = RsuManagementService.toIpv4Address(rsuIp);

        when(rsuRepository.findByIpv4Address(Inet4Address)).thenReturn(null);

        Rsu result = rsuUpgradeContextService.findRsuByIp(rsuIp);

        assertEquals(null, result);
        assertFalse(rsuUpgradeContextService.hasCompleteRsuData(rsuIp));
    }

    @Test
    void testFindRsuByIp_Success() throws UnknownHostException {
        String rsuIp = "10.0.0.12";
        Inet4Address Inet4Address = RsuManagementService.toIpv4Address(rsuIp);

        Rsu rsu = new Rsu();
        rsu.setIpv4Address(Inet4Address);

        when(rsuRepository.findByIpv4Address(Inet4Address)).thenReturn(rsu);

        Rsu result = rsuUpgradeContextService.findRsuByIp(rsuIp);

        assertSame(rsu, result);
    }

    @Test
    void testFindRsuByIp_InvalidIpAddress() {
        IllegalArgumentException exception = assertThrows(
                        IllegalArgumentException.class,
                () -> rsuUpgradeContextService.findRsuByIp("invalid-ip"));

        assertTrue(exception.getMessage().equals("Invalid IP address, should be like #.#.#.#, but was invalid-ip"));
    }
}
