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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.models.devices.management.ModifyRsuAllowedSelections;
import us.dot.its.jpo.ode.api.models.devices.management.RsuPatch;
import us.dot.its.jpo.ode.api.models.postgres.dtos.RsuInfoDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.SimplePosition;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.RsuManagementService;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RsuControllerTest {

    @Mock
    private RsuManagementService rsuManagementService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private RsuController rsuController;

    // ==================== GET ALL RSUS TESTS ====================

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

    // ==================== GET SINGLE RSU TESTS ====================

    @Test
    void testGetSingleRsuData_Success() {
        String rsuIp = "192.168.1.100";

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

        when(rsuManagementService.getRsuInfo(rsuIp)).thenReturn(rsuInfo);

        RsuInfoDto result = rsuController.getSingleRsuData(rsuIp);

        assertNotNull(result);

        assertEquals(rsuIp, result.getIpv4Address());
        assertEquals("I-25", result.getPrimaryRoute());

        verify(rsuManagementService).getRsuInfo(rsuIp);
    }

    @Test
    void testGetSingleRsuData_RsuNotFound() {
        String rsuIp = "192.168.1.999";

        when(rsuManagementService.getRsuInfo(rsuIp)).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> rsuController.getSingleRsuData(rsuIp));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("RSU not found", exception.getReason());

        verify(rsuManagementService).getRsuInfo(rsuIp);
    }

    @Test
    void testGetSingleRsuData_InvalidIpAddress() {
        String invalidRsuIp = "invalid-ip";

        when(rsuManagementService.getRsuInfo(invalidRsuIp))
                .thenThrow(new IllegalArgumentException("Invalid IP address: " + invalidRsuIp));

        assertThrows(
                IllegalArgumentException.class,
                () -> rsuController.getSingleRsuData(invalidRsuIp));

        verify(rsuManagementService).getRsuInfo(invalidRsuIp);
    }

    // ==================== GET ALLOWED SELECTIONS TESTS ====================

    @Test
    void testGetSingleRsuAllowedSelections_Success() {
        String username = "testuser@example.com";

        ModifyRsuAllowedSelections allowedSelections = new ModifyRsuAllowedSelections(
                Arrays.asList("I-25", "I-70"),
                Arrays.asList("Commsignia ITS-RS4-M", "Yunex RSU-2X"),
                Arrays.asList("ssh-group-1", "ssh-group-2"),
                Arrays.asList("snmp-group-1", "snmp-group-2"),
                Arrays.asList("v2c", "v3"),
                Arrays.asList("TestOrg", "OtherOrg"));

        when(rsuManagementService.getAllowedSelections(username)).thenReturn(allowedSelections);

        try (MockedStatic<PermissionService> mockedStatic = Mockito.mockStatic(PermissionService.class)) {
            mockedStatic.when(() -> PermissionService.getUsername(any())).thenReturn(username);

            ModifyRsuAllowedSelections result = rsuController.getSingleRsuAllowedSelections();

            assertNotNull(result);

            assertEquals(2, result.getPrimaryRoutes().size());
            assertEquals(2, result.getRsuModels().size());
            assertEquals(2, result.getSshCredentialGroups().size());
            assertEquals(2, result.getSnmpCredentialGroups().size());
            assertEquals(2, result.getSnmpVersionGroups().size());
            assertEquals(2, result.getOrganizations().size());

            verify(rsuManagementService).getAllowedSelections(username);
        }
    }

    // ==================== MODIFY RSU TESTS ====================

    @Test
    void testModifyRsu_Success() {
        String rsuIp = "192.168.1.100";
        RsuPatch patch = new RsuPatch();
        patch.setIpv4Address("192.168.1.101");

        doReturn(null).when(rsuManagementService).modifyRsu(rsuIp, patch);

        ResponseEntity<Void> result = rsuController.modifyRsu(rsuIp, patch);

        assertNotNull(result);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        assertNull(result.getBody());

        verify(rsuManagementService).modifyRsu(rsuIp, patch);
    }

    @Test
    void testModifyRsu_RsuNotFound() {
        String rsuIp = "192.168.1.999";
        RsuPatch patch = new RsuPatch();

        doThrow(new IllegalArgumentException("RSU not found"))
                .when(rsuManagementService).modifyRsu(rsuIp, patch);

        assertThrows(
                IllegalArgumentException.class,
                () -> rsuController.modifyRsu(rsuIp, patch));

        verify(rsuManagementService).modifyRsu(rsuIp, patch);
    }

    @Test
    void testModifyRsu_InvalidPatch() {
        String rsuIp = "192.168.1.100";
        RsuPatch invalidPatch = new RsuPatch();
        invalidPatch.setIpv4Address("invalid-ip");

        doThrow(new IllegalArgumentException("Invalid IP address"))
                .when(rsuManagementService).modifyRsu(rsuIp, invalidPatch);

        assertThrows(
                IllegalArgumentException.class,
                () -> rsuController.modifyRsu(rsuIp, invalidPatch));

        verify(rsuManagementService).modifyRsu(rsuIp, invalidPatch);
    }

    @Test
    void testModifyRsu_ServiceException() {
        String rsuIp = "192.168.1.100";
        RsuPatch patch = new RsuPatch();

        doThrow(new RuntimeException("Database error"))
                .when(rsuManagementService).modifyRsu(rsuIp, patch);

        assertThrows(
                RuntimeException.class,
                () -> rsuController.modifyRsu(rsuIp, patch));

        verify(rsuManagementService).modifyRsu(rsuIp, patch);
    }

    // ==================== DELETE SINGLE RSU TESTS ====================

    @Test
    void testDeleteRsu_Success() {
        String rsuIp = "192.168.1.100";

        doNothing().when(rsuManagementService).deleteRsuByIpv4Address(rsuIp);

        ResponseEntity<Void> result = rsuController.deleteRsu(rsuIp);

        assertNotNull(result);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        assertNull(result.getBody());

        verify(rsuManagementService).deleteRsuByIpv4Address(rsuIp);
    }

    @Test
    void testDeleteRsu_RsuNotFound() {
        String rsuIp = "192.168.1.999";

        doThrow(new IllegalArgumentException("RSU not found"))
                .when(rsuManagementService).deleteRsuByIpv4Address(rsuIp);

        assertThrows(
                IllegalArgumentException.class,
                () -> rsuController.deleteRsu(rsuIp));

        verify(rsuManagementService).deleteRsuByIpv4Address(rsuIp);
    }

    @Test
    void testDeleteRsu_InvalidIpAddress() {
        String invalidRsuIp = "invalid-ip";

        doThrow(new IllegalArgumentException("Invalid IP address: " + invalidRsuIp))
                .when(rsuManagementService).deleteRsuByIpv4Address(invalidRsuIp);

        assertThrows(
                IllegalArgumentException.class,
                () -> rsuController.deleteRsu(invalidRsuIp));

        verify(rsuManagementService).deleteRsuByIpv4Address(invalidRsuIp);
    }

    @Test
    void testDeleteRsu_ServiceException() {
        String rsuIp = "192.168.1.100";

        doThrow(new RuntimeException("Database connection failed"))
                .when(rsuManagementService).deleteRsuByIpv4Address(rsuIp);

        assertThrows(
                RuntimeException.class,
                () -> rsuController.deleteRsu(rsuIp));

        verify(rsuManagementService).deleteRsuByIpv4Address(rsuIp);
    }

    // ==================== DELETE MULTIPLE RSUS TESTS ====================

    @Test
    void testDeleteRsus_Success() {
        List<String> rsuIps = Arrays.asList("192.168.1.100", "192.168.1.101", "192.168.1.102");

        doNothing().when(rsuManagementService).deleteMultipleRsusByIpv4Address(rsuIps);

        ResponseEntity<Void> result = rsuController.deleteRsus(rsuIps);

        assertNotNull(result);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        assertNull(result.getBody());

        verify(rsuManagementService).deleteMultipleRsusByIpv4Address(rsuIps);
    }

    @Test
    void testDeleteRsus_SingleRsu() {
        List<String> rsuIps = Arrays.asList("192.168.1.100");

        doNothing().when(rsuManagementService).deleteMultipleRsusByIpv4Address(rsuIps);

        ResponseEntity<Void> result = rsuController.deleteRsus(rsuIps);

        assertNotNull(result);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());

        verify(rsuManagementService).deleteMultipleRsusByIpv4Address(rsuIps);
    }

    @Test
    void testDeleteRsus_EmptyList() {
        List<String> emptyList = Arrays.asList();

        doNothing().when(rsuManagementService).deleteMultipleRsusByIpv4Address(emptyList);

        ResponseEntity<Void> result = rsuController.deleteRsus(emptyList);

        assertNotNull(result);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());

        verify(rsuManagementService).deleteMultipleRsusByIpv4Address(emptyList);
    }

    @Test
    void testDeleteRsus_SomeNotFound() {
        List<String> rsuIps = Arrays.asList("192.168.1.100", "192.168.1.999", "192.168.1.101");

        doThrow(new IllegalArgumentException("Some RSUs not found"))
                .when(rsuManagementService).deleteMultipleRsusByIpv4Address(rsuIps);

        assertThrows(
                IllegalArgumentException.class,
                () -> rsuController.deleteRsus(rsuIps));

        verify(rsuManagementService).deleteMultipleRsusByIpv4Address(rsuIps);
    }

    @Test
    void testDeleteRsus_InvalidIpInList() {
        List<String> rsuIps = Arrays.asList("192.168.1.100", "invalid-ip", "192.168.1.101");

        doThrow(new IllegalArgumentException("Invalid IP address: invalid-ip"))
                .when(rsuManagementService).deleteMultipleRsusByIpv4Address(rsuIps);

        assertThrows(
                IllegalArgumentException.class,
                () -> rsuController.deleteRsus(rsuIps));

        verify(rsuManagementService).deleteMultipleRsusByIpv4Address(rsuIps);
    }

    @Test
    void testDeleteRsus_LargeList() {
        List<String> largeList = Arrays.asList(
                "192.168.1.1", "192.168.1.2", "192.168.1.3", "192.168.1.4", "192.168.1.5",
                "192.168.1.6", "192.168.1.7", "192.168.1.8", "192.168.1.9", "192.168.1.10");

        doNothing().when(rsuManagementService).deleteMultipleRsusByIpv4Address(largeList);

        ResponseEntity<Void> result = rsuController.deleteRsus(largeList);

        assertNotNull(result);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());

        verify(rsuManagementService).deleteMultipleRsusByIpv4Address(largeList);
    }

    @Test
    void testDeleteRsus_ServiceException() {
        List<String> rsuIps = Arrays.asList("192.168.1.100", "192.168.1.101");

        doThrow(new RuntimeException("Database transaction failed"))
                .when(rsuManagementService).deleteMultipleRsusByIpv4Address(rsuIps);

        assertThrows(
                RuntimeException.class,
                () -> rsuController.deleteRsus(rsuIps));

        verify(rsuManagementService).deleteMultipleRsusByIpv4Address(rsuIps);
    }

    // ==================== CREATE RSU TESTS ====================

    @Test
    void testCreateRsu_Success() {
        String username = "testuser@example.com";

        RsuInfoDto rsuInfoDto = new RsuInfoDto(
                "192.168.1.100",
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

        Rsu mockRsu = new Rsu();
        List<String> qualifiedOrgs = Arrays.asList("TestOrg", "OtherOrg");

        try (MockedStatic<SecurityContextHolder> mockedSecurityContext = Mockito
                .mockStatic(SecurityContextHolder.class);
                MockedStatic<PermissionService> mockedPermissionService = Mockito.mockStatic(PermissionService.class)) {

            mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            mockedPermissionService.when(() -> PermissionService.getUsername(authentication)).thenReturn(username);

            when(permissionService.getQualifiedOrgList(username, "OPERATOR")).thenReturn(qualifiedOrgs);
            when(rsuManagementService.createRsu(rsuInfoDto)).thenReturn(mockRsu);
            doNothing().when(rsuManagementService).createRsuOrgRelationship(anyString(), any(Rsu.class));

            ResponseEntity<Void> result = rsuController.createRsu(rsuInfoDto);

            assertNotNull(result);
            assertEquals(HttpStatus.CREATED, result.getStatusCode());
            assertNull(result.getBody());

            verify(permissionService).getQualifiedOrgList(username, "OPERATOR");
            verify(rsuManagementService).createRsu(rsuInfoDto);
            verify(rsuManagementService).createRsuOrgRelationship("TestOrg", mockRsu);
        }
    }

    @Test
    void testCreateRsu_MultipleOrganizations() {
        String username = "testuser@example.com";

        RsuInfoDto rsuInfoDto = new RsuInfoDto(
                "192.168.1.100",
                new SimplePosition(39.7392, -105.0844),
                123.4,
                "I-25",
                "RSU123",
                "SCMS123",
                "Commsignia ITS-RS4-M",
                "ssh-group-1",
                "snmp-group-1",
                "v3",
                Arrays.asList("TestOrg", "OtherOrg", "ThirdOrg"));

        Rsu mockRsu = new Rsu();
        List<String> qualifiedOrgs = Arrays.asList("TestOrg", "OtherOrg", "ThirdOrg");

        try (MockedStatic<SecurityContextHolder> mockedSecurityContext = Mockito
                .mockStatic(SecurityContextHolder.class);
                MockedStatic<PermissionService> mockedPermissionService = Mockito.mockStatic(PermissionService.class)) {

            mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            mockedPermissionService.when(() -> PermissionService.getUsername(authentication)).thenReturn(username);

            when(permissionService.getQualifiedOrgList(username, "OPERATOR")).thenReturn(qualifiedOrgs);
            when(rsuManagementService.createRsu(rsuInfoDto)).thenReturn(mockRsu);
            doNothing().when(rsuManagementService).createRsuOrgRelationship(anyString(), any(Rsu.class));

            ResponseEntity<Void> result = rsuController.createRsu(rsuInfoDto);

            assertNotNull(result);
            assertEquals(HttpStatus.CREATED, result.getStatusCode());

            verify(rsuManagementService).createRsu(rsuInfoDto);
            verify(rsuManagementService).createRsuOrgRelationship("TestOrg", mockRsu);
            verify(rsuManagementService).createRsuOrgRelationship("OtherOrg", mockRsu);
            verify(rsuManagementService).createRsuOrgRelationship("ThirdOrg", mockRsu);
            verify(rsuManagementService, times(3)).createRsuOrgRelationship(anyString(), eq(mockRsu));
        }
    }

    @Test
    void testCreateRsu_UnqualifiedOrganization() {
        String username = "testuser@example.com";

        RsuInfoDto rsuInfoDto = new RsuInfoDto(
                "192.168.1.100",
                new SimplePosition(39.7392, -105.0844),
                123.4,
                "I-25",
                "RSU123",
                "SCMS123",
                "Commsignia ITS-RS4-M",
                "ssh-group-1",
                "snmp-group-1",
                "v3",
                Arrays.asList("TestOrg", "UnqualifiedOrg"));

        List<String> qualifiedOrgs = Arrays.asList("TestOrg");

        try (MockedStatic<SecurityContextHolder> mockedSecurityContext = Mockito
                .mockStatic(SecurityContextHolder.class);
                MockedStatic<PermissionService> mockedPermissionService = Mockito.mockStatic(PermissionService.class)) {

            mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            mockedPermissionService.when(() -> PermissionService.getUsername(authentication)).thenReturn(username);

            when(permissionService.getQualifiedOrgList(username, "OPERATOR")).thenReturn(qualifiedOrgs);

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> rsuController.createRsu(rsuInfoDto));

            assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
            assertTrue(exception.getReason().contains("User not qualified to modify organizations"));
            assertTrue(exception.getReason().contains("UnqualifiedOrg"));

            verify(permissionService).getQualifiedOrgList(username, "OPERATOR");
            verify(rsuManagementService, never()).createRsu(any());
            verify(rsuManagementService, never()).createRsuOrgRelationship(anyString(), any());
        }
    }

    @Test
    void testCreateRsu_MultipleUnqualifiedOrganizations() {
        String username = "testuser@example.com";

        RsuInfoDto rsuInfoDto = new RsuInfoDto(
                "192.168.1.100",
                new SimplePosition(39.7392, -105.0844),
                123.4,
                "I-25",
                "RSU123",
                "SCMS123",
                "Commsignia ITS-RS4-M",
                "ssh-group-1",
                "snmp-group-1",
                "v3",
                Arrays.asList("TestOrg", "UnqualifiedOrg1", "UnqualifiedOrg2"));

        List<String> qualifiedOrgs = Arrays.asList("TestOrg");

        try (MockedStatic<SecurityContextHolder> mockedSecurityContext = Mockito
                .mockStatic(SecurityContextHolder.class);
                MockedStatic<PermissionService> mockedPermissionService = Mockito.mockStatic(PermissionService.class)) {

            mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            mockedPermissionService.when(() -> PermissionService.getUsername(authentication)).thenReturn(username);

            when(permissionService.getQualifiedOrgList(username, "OPERATOR")).thenReturn(qualifiedOrgs);

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> rsuController.createRsu(rsuInfoDto));

            assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
            assertTrue(exception.getReason().contains("User not qualified to modify organizations"));
            assertTrue(exception.getReason().contains("UnqualifiedOrg1"));
            assertTrue(exception.getReason().contains("UnqualifiedOrg2"));

            verify(rsuManagementService, never()).createRsu(any());
        }
    }

    @Test
    void testCreateRsu_NonexistentOrganization() {
        String username = "testuser@example.com";

        RsuInfoDto rsuInfoDto = new RsuInfoDto(
                "192.168.1.100",
                new SimplePosition(39.7392, -105.0844),
                123.4,
                "I-25",
                "RSU123",
                "SCMS123",
                "Commsignia ITS-RS4-M",
                "ssh-group-1",
                "snmp-group-1",
                "v3",
                Arrays.asList("NonexistentOrg"));

        List<String> qualifiedOrgs = Arrays.asList("TestOrg");

        try (MockedStatic<SecurityContextHolder> mockedSecurityContext = Mockito
                .mockStatic(SecurityContextHolder.class);
                MockedStatic<PermissionService> mockedPermissionService = Mockito.mockStatic(PermissionService.class)) {

            mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            mockedPermissionService.when(() -> PermissionService.getUsername(authentication)).thenReturn(username);

            when(permissionService.getQualifiedOrgList(username, "OPERATOR")).thenReturn(qualifiedOrgs);

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> rsuController.createRsu(rsuInfoDto));

            assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
            assertTrue(exception.getReason().contains("NonexistentOrg"));

            verify(rsuManagementService, never()).createRsu(any());
        }
    }

    @Test
    void testCreateRsu_DuplicateIpAddress() {
        String username = "testuser@example.com";

        RsuInfoDto rsuInfoDto = new RsuInfoDto(
                "192.168.1.100",
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

        List<String> qualifiedOrgs = Arrays.asList("TestOrg");

        try (MockedStatic<SecurityContextHolder> mockedSecurityContext = Mockito
                .mockStatic(SecurityContextHolder.class);
                MockedStatic<PermissionService> mockedPermissionService = Mockito.mockStatic(PermissionService.class)) {

            mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            mockedPermissionService.when(() -> PermissionService.getUsername(authentication)).thenReturn(username);

            when(permissionService.getQualifiedOrgList(username, "OPERATOR")).thenReturn(qualifiedOrgs);
            when(rsuManagementService.createRsu(rsuInfoDto))
                    .thenThrow(new IllegalArgumentException("RSU with IP 192.168.1.100 already exists"));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> rsuController.createRsu(rsuInfoDto));

            verify(rsuManagementService).createRsu(rsuInfoDto);
            verify(rsuManagementService, never()).createRsuOrgRelationship(anyString(), any());
        }
    }

    @Test
    void testCreateRsu_EmptyOrganizationsList() {
        String username = "testuser@example.com";

        RsuInfoDto rsuInfoDto = new RsuInfoDto(
                "192.168.1.100",
                new SimplePosition(39.7392, -105.0844),
                123.4,
                "I-25",
                "RSU123",
                "SCMS123",
                "Commsignia ITS-RS4-M",
                "ssh-group-1",
                "snmp-group-1",
                "v3",
                Arrays.asList());

        Rsu mockRsu = new Rsu();
        List<String> qualifiedOrgs = Arrays.asList("TestOrg");

        try (MockedStatic<SecurityContextHolder> mockedSecurityContext = Mockito
                .mockStatic(SecurityContextHolder.class);
                MockedStatic<PermissionService> mockedPermissionService = Mockito.mockStatic(PermissionService.class)) {

            mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            mockedPermissionService.when(() -> PermissionService.getUsername(authentication)).thenReturn(username);

            when(permissionService.getQualifiedOrgList(username, "OPERATOR")).thenReturn(qualifiedOrgs);
            when(rsuManagementService.createRsu(rsuInfoDto)).thenReturn(mockRsu);

            ResponseEntity<Void> result = rsuController.createRsu(rsuInfoDto);

            assertNotNull(result);
            assertEquals(HttpStatus.CREATED, result.getStatusCode());

            verify(rsuManagementService).createRsu(rsuInfoDto);
            verify(rsuManagementService, never()).createRsuOrgRelationship(anyString(), any());
        }
    }

    @Test
    void testCreateRsu_ServiceException() {
        String username = "testuser@example.com";

        RsuInfoDto rsuInfoDto = new RsuInfoDto(
                "192.168.1.100",
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

        List<String> qualifiedOrgs = Arrays.asList("TestOrg");

        try (MockedStatic<SecurityContextHolder> mockedSecurityContext = Mockito
                .mockStatic(SecurityContextHolder.class);
                MockedStatic<PermissionService> mockedPermissionService = Mockito.mockStatic(PermissionService.class)) {

            mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            mockedPermissionService.when(() -> PermissionService.getUsername(authentication)).thenReturn(username);

            when(permissionService.getQualifiedOrgList(username, "OPERATOR")).thenReturn(qualifiedOrgs);
            when(rsuManagementService.createRsu(rsuInfoDto))
                    .thenThrow(new RuntimeException("Database connection failed"));

            assertThrows(
                    RuntimeException.class,
                    () -> rsuController.createRsu(rsuInfoDto));

            verify(rsuManagementService).createRsu(rsuInfoDto);
        }
    }

    @Test
    void testCreateRsu_OrgRelationshipCreationFails() {
        String username = "testuser@example.com";

        RsuInfoDto rsuInfoDto = new RsuInfoDto(
                "192.168.1.100",
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

        Rsu mockRsu = new Rsu();
        List<String> qualifiedOrgs = Arrays.asList("TestOrg");

        try (MockedStatic<SecurityContextHolder> mockedSecurityContext = Mockito
                .mockStatic(SecurityContextHolder.class);
                MockedStatic<PermissionService> mockedPermissionService = Mockito.mockStatic(PermissionService.class)) {

            mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            mockedPermissionService.when(() -> PermissionService.getUsername(authentication)).thenReturn(username);

            when(permissionService.getQualifiedOrgList(username, "OPERATOR")).thenReturn(qualifiedOrgs);
            when(rsuManagementService.createRsu(rsuInfoDto)).thenReturn(mockRsu);
            doThrow(new RuntimeException("Failed to create organization relationship"))
                    .when(rsuManagementService).createRsuOrgRelationship("TestOrg", mockRsu);

            assertThrows(
                    RuntimeException.class,
                    () -> rsuController.createRsu(rsuInfoDto));

            verify(rsuManagementService).createRsu(rsuInfoDto);
            verify(rsuManagementService).createRsuOrgRelationship("TestOrg", mockRsu);
        }
    }

    @Test
    void testCreateRsu_NullOrganizationsList() {
        String username = "testuser@example.com";

        RsuInfoDto rsuInfoDto = new RsuInfoDto(
                "192.168.1.100",
                new SimplePosition(39.7392, -105.0844),
                123.4,
                "I-25",
                "RSU123",
                "SCMS123",
                "Commsignia ITS-RS4-M",
                "ssh-group-1",
                "snmp-group-1",
                "v3",
                null);

        List<String> qualifiedOrgs = Arrays.asList("TestOrg");

        try (MockedStatic<SecurityContextHolder> mockedSecurityContext = Mockito
                .mockStatic(SecurityContextHolder.class);
                MockedStatic<PermissionService> mockedPermissionService = Mockito.mockStatic(PermissionService.class)) {

            mockedSecurityContext.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            mockedPermissionService.when(() -> PermissionService.getUsername(authentication)).thenReturn(username);

            when(permissionService.getQualifiedOrgList(username, "OPERATOR")).thenReturn(qualifiedOrgs);

            assertThrows(
                    NullPointerException.class,
                    () -> rsuController.createRsu(rsuInfoDto));

            verify(rsuManagementService, never()).createRsu(any());
        }
    }
}
