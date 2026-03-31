package us.dot.its.jpo.ode.api.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareImage;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpgradeRule;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.repositories.FirmwareUpgradeRuleRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

@ExtendWith(MockitoExtension.class)
class RsuUpgradeServiceTest {

    @Mock
    private RsuUpgradeContextService rsuUpgradeContextService;

    @Mock
    private FirmwareUpgradeRuleRepository firmwareUpgradeRuleRepository;

    @Mock
    private RsuRepository rsuRepository;

    @InjectMocks
    private RsuUpgradeService rsuUpgradeService;

    @Test
    void testCheckFirmwareUpgrade_Success() throws UnknownHostException {
        String organization = "TestOrg";
        String rsuIp = "10.0.0.10";

        FirmwareImage currentImage = new FirmwareImage();
        currentImage.setId(1);

        FirmwareImage targetImage = new FirmwareImage();
        targetImage.setId(2);
        targetImage.setName("RSU Firmware 2.0");
        targetImage.setVersion("2.0");

        FirmwareUpgradeRule rule = new FirmwareUpgradeRule();
        rule.setFrom(currentImage);
        rule.setTo(targetImage);

        Rsu rsu = new Rsu();
        rsu.setIpv4Address(InetAddress.getByName(rsuIp));
        rsu.setFirmwareVersion(currentImage);

        when(rsuUpgradeContextService.findRsuForOrganization(rsuIp, organization)).thenReturn(rsu);
        when(firmwareUpgradeRuleRepository.findFirstByFrom_Id(1)).thenReturn(Optional.of(rule));

        Map<String, Object> result = rsuUpgradeService.checkFirmwareUpgrade(organization, rsuIp);

        assertEquals(true, result.get("upgrade_available"));
        assertEquals(2, result.get("upgrade_id"));
        assertEquals("RSU Firmware 2.0", result.get("upgrade_name"));
        assertEquals("2.0", result.get("upgrade_version"));
    }

    @Test
    void testStartFirmwareUpgradeForRsus_ThrowsWhenRsuDataMissing() {
        String organization = "TestOrg";
        String successIp = "10.0.0.10";
        String missingIp = "10.0.0.11";

        RsuUpgradeService serviceSpy = spy(rsuUpgradeService);
        when(rsuUpgradeContextService.hasCompleteRsuData(successIp, organization)).thenReturn(true);
        when(rsuUpgradeContextService.hasCompleteRsuData(missingIp, organization)).thenReturn(false);
        doReturn(new RsuUpgradeService.UpgradeExecutionResult(Map.of("message", "started"), 201))
                .when(serviceSpy).markRsuForUpgrade(successIp, organization);

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> serviceSpy.startFirmwareUpgradeForRsus(organization, List.of(successIp, missingIp)));

        assertTrue(exception.getMessage().contains("does not have complete RSU data"));
    }

    @Test
    void testStartFirmwareUpgradeForRsus_PropagatesFirmwareUpgradeUnavailableException() {
        String organization = "TestOrg";
        String rsuIp = "10.0.0.12";

        RsuUpgradeService serviceSpy = spy(rsuUpgradeService);
        when(rsuUpgradeContextService.hasCompleteRsuData(rsuIp, organization)).thenReturn(true);
        doThrow(new RsuUpgradeService.FirmwareUpgradeUnavailableException("Requested RSU is already up to date"))
                .when(serviceSpy).markRsuForUpgrade(rsuIp, organization);

        RsuUpgradeService.FirmwareUpgradeUnavailableException exception = assertThrows(
                RsuUpgradeService.FirmwareUpgradeUnavailableException.class,
                () -> serviceSpy.startFirmwareUpgradeForRsus(organization, List.of(rsuIp)));

        assertEquals("Requested RSU is already up to date", exception.getMessage());
    }

    @Test
    void testMarkRsuForUpgrade_SuccessPostsJsonAndSavesTargetVersion() throws UnknownHostException {
        String organization = "TestOrg";
        String rsuIp = "10.0.0.13";
        String endpoint = "http://firmware-manager";

        RestTemplate restTemplate = org.mockito.Mockito.mock(RestTemplate.class);
        ReflectionTestUtils.setField(rsuUpgradeService, "firmwareManagerEndpoint", endpoint);
        ReflectionTestUtils.setField(rsuUpgradeService, "restTemplate", restTemplate);

        FirmwareImage currentImage = new FirmwareImage();
        currentImage.setId(10);

        FirmwareImage targetImage = new FirmwareImage();
        targetImage.setId(11);
        targetImage.setName("Target Firmware");
        targetImage.setVersion("11.0");

        FirmwareUpgradeRule rule = new FirmwareUpgradeRule();
        rule.setFrom(currentImage);
        rule.setTo(targetImage);

        Rsu rsu = new Rsu();
        rsu.setIpv4Address(InetAddress.getByName(rsuIp));
        rsu.setFirmwareVersion(currentImage);

        when(rsuUpgradeContextService.findRsuForOrganization(rsuIp, organization)).thenReturn(rsu);
        when(firmwareUpgradeRuleRepository.findFirstByFrom_Id(10)).thenReturn(Optional.of(rule));
        when(rsuRepository.save(any(Rsu.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.postForEntity(eq(endpoint + "/init_firmware_upgrade"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("message", "started"), HttpStatus.OK));

        RsuUpgradeService.UpgradeExecutionResult result = rsuUpgradeService.markRsuForUpgrade(rsuIp, organization);

        assertEquals(200, result.statusCode());
        assertEquals(Map.of("message", "started"), result.body());
        assertEquals(targetImage, rsu.getTargetFirmwareVersion());

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(endpoint + "/init_firmware_upgrade"), entityCaptor.capture(),
                eq(Map.class));

        HttpEntity requestEntity = entityCaptor.getValue();
        assertNotNull(requestEntity);
        assertEquals(MediaType.APPLICATION_JSON, requestEntity.getHeaders().getContentType());
        assertEquals(Map.of("rsu_ip", rsuIp), requestEntity.getBody());
    }

    @Test
    void testMarkRsuForUpgrade_ThrowsConflictWhenAlreadyUpToDate() throws UnknownHostException {
        String organization = "TestOrg";
        String rsuIp = "10.0.0.14";

        ReflectionTestUtils.setField(rsuUpgradeService, "firmwareManagerEndpoint", "http://firmware-manager");

        FirmwareImage currentImage = new FirmwareImage();
        currentImage.setId(20);

        Rsu rsu = new Rsu();
        rsu.setIpv4Address(InetAddress.getByName(rsuIp));
        rsu.setFirmwareVersion(currentImage);

        when(rsuUpgradeContextService.findRsuForOrganization(rsuIp, organization)).thenReturn(rsu);
        when(firmwareUpgradeRuleRepository.findFirstByFrom_Id(20)).thenReturn(Optional.empty());

        RsuUpgradeService.FirmwareUpgradeUnavailableException exception = assertThrows(
                RsuUpgradeService.FirmwareUpgradeUnavailableException.class,
                () -> rsuUpgradeService.markRsuForUpgrade(rsuIp, organization));

        assertTrue(exception.getMessage().contains("already up to date"));
    }
}
