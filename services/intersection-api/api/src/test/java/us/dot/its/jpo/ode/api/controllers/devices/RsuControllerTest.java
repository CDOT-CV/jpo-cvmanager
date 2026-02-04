package us.dot.its.jpo.ode.api.controllers.devices;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.models.devices.management.ModifyRsuAllowedSelections;
import us.dot.its.jpo.ode.api.models.postgres.dtos.RsuInfoDto;
import us.dot.its.jpo.ode.api.models.SimplePosition;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.RsuManagementService;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RsuControllerTest {

    @Mock
    private RsuManagementService rsuManagementService;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private RsuController rsuController;

    @Test
    void testGetAllRsus_Success() {
        String organization = "TestOrg";
        Pageable pageable = PageRequest.of(0, 100);

        RsuInfoDto rsu1 = new RsuInfoDto(
                "192.168.1.100",
                new SimplePosition(39.7392, -105.0844),
                123.4,
                "I-25",
                "RSU1",
                "SCMS1",
                "Commsignia ITS-RS4-M",
                "ssh-group-1",
                "snmp-group-1",
                "v3",
                Arrays.asList("TestOrg"));

        RsuInfoDto rsu2 = new RsuInfoDto(
                "192.168.1.101",
                new SimplePosition(39.7400, -105.0850),
                124.5,
                "I-70",
                "RSU2",
                "SCMS2",
                "Yunex RSU-2X",
                "ssh-group-2",
                "snmp-group-2",
                "v2c",
                Arrays.asList("TestOrg"));

        List<RsuInfoDto> rsuList = Arrays.asList(rsu1, rsu2);
        Page<RsuInfoDto> rsuPage = new PageImpl<>(rsuList, pageable, 2);

        when(rsuManagementService.getAllRsuInfo(organization, pageable)).thenReturn(rsuPage);

        Page<RsuInfoDto> result = rsuController.getAllRsus(organization, pageable);

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        assertEquals("192.168.1.100", result.getContent().get(0).getIpv4Address());
        assertEquals("192.168.1.101", result.getContent().get(1).getIpv4Address());

        verify(rsuManagementService).getAllRsuInfo(organization, pageable);
    }

    @Test
    void testGetAllRsus_EmptyResult() {
        String organization = "EmptyOrg";
        Pageable pageable = PageRequest.of(0, 100);
        Page<RsuInfoDto> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(rsuManagementService.getAllRsuInfo(organization, pageable)).thenReturn(emptyPage);

        Page<RsuInfoDto> result = rsuController.getAllRsus(organization, pageable);

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());

        verify(rsuManagementService).getAllRsuInfo(organization, pageable);
    }

    @Test
    void testGetAllRsus_WithCustomPageSize() {
        String organization = "TestOrg";
        Pageable pageable = PageRequest.of(0, 50);

        RsuInfoDto rsu1 = new RsuInfoDto(
                "192.168.1.100",
                new SimplePosition(39.7392, -105.0844),
                123.4,
                "I-25",
                "RSU1",
                "SCMS1",
                "Model X",
                "ssh-group",
                "snmp-group",
                "v3",
                Arrays.asList("TestOrg"));

        Page<RsuInfoDto> rsuPage = new PageImpl<>(List.of(rsu1), pageable, 1);

        when(rsuManagementService.getAllRsuInfo(organization, pageable)).thenReturn(rsuPage);

        Page<RsuInfoDto> result = rsuController.getAllRsus(organization, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(50, result.getPageable().getPageSize());

        verify(rsuManagementService).getAllRsuInfo(organization, pageable);
    }

    @Test
    void testGetSingleRsuData_Success() {
        String organization = "TestOrg";
        String rsuIp = "192.168.1.100";
        String username = "testuser@example.com";

        RsuInfoDto rsuInfo = new RsuInfoDto(
                rsuIp,
                new SimplePosition(39.7392, -105.0844),
                123.4,
                "I-25",
                "RSU123",
                "SCMS123",
                "Commsignia ITS-RS4-M",
                "ssh-group-1",
                "snmp-group-1",
                "v3",
                Arrays.asList("TestOrg"));

        ModifyRsuAllowedSelections allowedSelections = new ModifyRsuAllowedSelections(
                Arrays.asList("I-25", "I-70"),
                Arrays.asList("Commsignia ITS-RS4-M", "Yunex RSU-2X"),
                Arrays.asList("ssh-group-1", "ssh-group-2"),
                Arrays.asList("snmp-group-1", "snmp-group-2"),
                Arrays.asList("v2c", "v3"),
                Arrays.asList("TestOrg", "OtherOrg"));

        when(rsuManagementService.getRsuInfo(rsuIp)).thenReturn(rsuInfo);
        when(rsuManagementService.getAllowedSelections(username)).thenReturn(allowedSelections);

        try (MockedStatic<PermissionService> mockedStatic = Mockito.mockStatic(PermissionService.class)) {
            mockedStatic.when(() -> PermissionService.getUsername(any())).thenReturn(username);

            RsuInfoDto result = rsuController.getSingleRsuData(rsuIp);

            assertNotNull(result);

            assertEquals(rsuIp, result.getIpv4Address());
            assertEquals("I-25", result.getPrimaryRoute());

            verify(rsuManagementService).getRsuInfo(rsuIp);
            verify(rsuManagementService).getAllowedSelections(username);
        }
    }

    @Test
    void testGetSingleRsuData_RsuNotFound() {
        String organization = "TestOrg";
        String rsuIp = "192.168.1.999";

        when(rsuManagementService.getRsuInfo(rsuIp)).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> rsuController.getSingleRsuData(rsuIp));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("RSU not found", exception.getReason());

        verify(rsuManagementService).getRsuInfo(rsuIp);
        verify(rsuManagementService, never()).getAllowedSelections(any());
    }

    @Test
    void testGetSingleRsuData_InvalidIpAddress() {
        String organization = "TestOrg";
        String invalidRsuIp = "invalid-ip";

        when(rsuManagementService.getRsuInfo(invalidRsuIp))
                .thenThrow(new IllegalArgumentException("Invalid IP address: " + invalidRsuIp));

        assertThrows(
                IllegalArgumentException.class,
                () -> rsuController.getSingleRsuData(invalidRsuIp));

        verify(rsuManagementService).getRsuInfo(invalidRsuIp);
        verify(rsuManagementService, never()).getAllowedSelections(any());
    }

    @Test
    void testGetSingleRsuAllowedSelections_Success() {
        String organization = "TestOrg";
        String rsuIp = "192.168.1.100";
        String username = "testuser@example.com";

        RsuInfoDto rsuInfo = new RsuInfoDto(
                rsuIp,
                new SimplePosition(39.7392, -105.0844),
                123.4,
                "I-25",
                "RSU123",
                "SCMS123",
                "Commsignia ITS-RS4-M",
                "ssh-group-1",
                "snmp-group-1",
                "v3",
                Arrays.asList("TestOrg"));

        ModifyRsuAllowedSelections allowedSelections = new ModifyRsuAllowedSelections(
                Arrays.asList("I-25", "I-70"),
                Arrays.asList("Commsignia ITS-RS4-M", "Yunex RSU-2X"),
                Arrays.asList("ssh-group-1", "ssh-group-2"),
                Arrays.asList("snmp-group-1", "snmp-group-2"),
                Arrays.asList("v2c", "v3"),
                Arrays.asList("TestOrg", "OtherOrg"));

        when(rsuManagementService.getRsuInfo(rsuIp)).thenReturn(rsuInfo);
        when(rsuManagementService.getAllowedSelections(username)).thenReturn(allowedSelections);

        try (MockedStatic<PermissionService> mockedStatic = Mockito.mockStatic(PermissionService.class)) {
            mockedStatic.when(() -> PermissionService.getUsername(any())).thenReturn(username);

            ModifyRsuAllowedSelections result = rsuController.getSingleRsuAllowedSelections(rsuIp);

            assertNotNull(result);

            assertEquals(2, result.getPrimaryRoutes().size());
            assertEquals(2, result.getRsuModels().size());
            assertEquals(2, result.getSshCredentialGroups().size());
            assertEquals(2, result.getSnmpCredentialGroups().size());
            assertEquals(2, result.getSnmpVersionGroups().size());
            assertEquals(2, result.getOrganizations().size());

            verify(rsuManagementService).getRsuInfo(rsuIp);
            verify(rsuManagementService).getAllowedSelections(username);
        }
    }

    @Test
    void testGetSingleRsuAllowedSelections_RsuNotFound() {
        String organization = "TestOrg";
        String rsuIp = "192.168.1.999";

        when(rsuManagementService.getRsuInfo(rsuIp)).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> rsuController.getSingleRsuAllowedSelections(rsuIp));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("RSU not found", exception.getReason());

        verify(rsuManagementService).getRsuInfo(rsuIp);
        verify(rsuManagementService, never()).getAllowedSelections(any());
    }

    @Test
    void testGetSingleRsuAllowedSelections_InvalidIpAddress() {
        String organization = "TestOrg";
        String invalidRsuIp = "invalid-ip";

        when(rsuManagementService.getRsuInfo(invalidRsuIp))
                .thenThrow(new IllegalArgumentException("Invalid IP address: " + invalidRsuIp));

        assertThrows(
                IllegalArgumentException.class,
                () -> rsuController.getSingleRsuAllowedSelections(invalidRsuIp));

        verify(rsuManagementService).getRsuInfo(invalidRsuIp);
        verify(rsuManagementService, never()).getAllowedSelections(any());
    }
}
