package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import us.dot.its.jpo.ode.api.models.devices.management.RsuUpgradeRequest;
import us.dot.its.jpo.ode.api.services.RsuUpgradeService;

@ExtendWith(MockitoExtension.class)
class UpgradeControllerTest {

    @Mock
    private RsuUpgradeService rsuUpgradeService;

    @InjectMocks
    private UpgradeController upgradeController;

    @Test
    void testStartUpgrade_Success() {
        String organization = "TestOrg";
        List<String> rsuIps = List.of("10.0.0.10", "10.0.0.11");

        RsuUpgradeRequest request = new RsuUpgradeRequest();
        request.setRsuIp(rsuIps);

        Map<String, Object> serviceResponse = Map.of(
                "10.0.0.10", Map.of("code", 201, "data", Map.of("message", "started")));

        when(rsuUpgradeService.startFirmwareUpgradeForRsus(organization, rsuIps)).thenReturn(serviceResponse);

        ResponseEntity<Map<String, Object>> response = upgradeController.startUpgrade(organization, request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(serviceResponse, response.getBody());
        verify(rsuUpgradeService).startFirmwareUpgradeForRsus(organization, rsuIps);
    }

    @Test
    void testCheckUpgrade_Success() {
        String organization = "TestOrg";
        List<String> rsuIps = List.of("10.0.0.10");

        RsuUpgradeRequest request = new RsuUpgradeRequest();
        request.setRsuIp(rsuIps);

        Map<String, Object> serviceResponse = Map.of(
                "upgrade_available", true,
                "upgrade_id", 2,
                "upgrade_name", "RSU Firmware 2.0",
                "upgrade_version", "2.0");

        when(rsuUpgradeService.checkFirmwareUpgrade(organization, rsuIps)).thenReturn(serviceResponse);

        ResponseEntity<Map<String, Object>> response = upgradeController.checkUpgrade(organization, request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(serviceResponse, response.getBody());
        verify(rsuUpgradeService).checkFirmwareUpgrade(organization, rsuIps);
    }
}
