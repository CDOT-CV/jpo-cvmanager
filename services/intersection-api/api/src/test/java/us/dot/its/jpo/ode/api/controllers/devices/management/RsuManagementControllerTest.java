package us.dot.its.jpo.ode.api.controllers.devices.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.models.devices.management.GetModifyRsuData;
import us.dot.its.jpo.ode.api.models.devices.management.GetModifyRsuDataSingle;
import us.dot.its.jpo.ode.api.models.devices.management.ModifyRsuAllowedSelections;
import us.dot.its.jpo.ode.api.models.postgres.derived.RsuDetailedInfoRow;
import us.dot.its.jpo.ode.api.repositories.RsusRepository;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.RsuManagementService;

class RsuManagementControllerTest {

    private static final GeometryFactory geometryFactory = new GeometryFactory();

    private RsuManagementController controller;

    @Mock
    private RsusRepository rsusRepository;

    @Mock
    private RsuManagementService rsuManagementService;

    @Mock
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new RsuManagementController(rsusRepository, rsuManagementService);
    }

    /**
     * Helper method to create a test RsuDetailedInfoRow
     */
    private RsuDetailedInfoRow createTestRow(String ip, double lat, double lon, String orgName) {
        RsuDetailedInfoRow row = new RsuDetailedInfoRow();
        row.setIpv4Address(ip);

        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
        row.setGeometry(point);

        row.setMilepost(100.5f);
        row.setPrimaryRoute("I-25");
        row.setSerialNumber("SN12345");
        row.setIssScmsId("SCMS001");
        row.setModel("Commsignia ITS-RS4-M");
        row.setSshCredential("ssh_group1");
        row.setSnmpCredential("snmp_group1");
        row.setSnmpVersion("41");
        row.setOrgName(orgName);

        return row;
    }

    @Test
    void testGetAllRsus_singleRsuSingleOrganization() {
        // Arrange
        String organization = "CDOT";
        List<RsuDetailedInfoRow> rows = List.of(
                createTestRow("192.168.1.1", 39.7392, -104.9903, organization));

        when(rsusRepository.findAllDetailedRsuInfoRowsByOrganization(organization)).thenReturn(rows);

        // Act
        GetModifyRsuData result = controller.getAllRsus(organization);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getRsuData());
        assertEquals(1, result.getRsuData().size());
        assertEquals("192.168.1.1", result.getRsuData().get(0).getIp());
        assertEquals(1, result.getRsuData().get(0).getOrganizations().size());
        assertEquals("CDOT", result.getRsuData().get(0).getOrganizations().get(0));

        verify(rsusRepository, times(1)).findAllDetailedRsuInfoRowsByOrganization(organization);
    }

    @Test
    void testGetAllRsus_singleRsuMultipleOrganizations() {
        // Arrange
        String organization = "CDOT";
        List<RsuDetailedInfoRow> rows = Arrays.asList(
                createTestRow("192.168.1.1", 39.7392, -104.9903, "CDOT"),
                createTestRow("192.168.1.1", 39.7392, -104.9903, "City of Denver"));

        when(rsusRepository.findAllDetailedRsuInfoRowsByOrganization(organization)).thenReturn(rows);

        // Act
        GetModifyRsuData result = controller.getAllRsus(organization);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getRsuData().size());
        assertEquals("192.168.1.1", result.getRsuData().get(0).getIp());
        assertEquals(2, result.getRsuData().get(0).getOrganizations().size());
        assertTrue(result.getRsuData().get(0).getOrganizations().contains("CDOT"));
        assertTrue(result.getRsuData().get(0).getOrganizations().contains("City of Denver"));

        verify(rsusRepository, times(1)).findAllDetailedRsuInfoRowsByOrganization(organization);
    }

    @Test
    void testGetAllRsus_multipleRsus() {
        // Arrange
        String organization = "CDOT";
        List<RsuDetailedInfoRow> rows = Arrays.asList(
                createTestRow("192.168.1.1", 39.7392, -104.9903, organization),
                createTestRow("192.168.1.2", 39.7500, -105.0000, organization),
                createTestRow("192.168.1.3", 39.7600, -105.0100, organization));

        when(rsusRepository.findAllDetailedRsuInfoRowsByOrganization(organization)).thenReturn(rows);

        // Act
        GetModifyRsuData result = controller.getAllRsus(organization);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.getRsuData().size());

        verify(rsusRepository, times(1)).findAllDetailedRsuInfoRowsByOrganization(organization);
    }

    @Test
    void testGetAllRsus_emptyResult() {
        // Arrange
        String organization = "Empty Org";
        when(rsusRepository.findAllDetailedRsuInfoRowsByOrganization(organization))
                .thenReturn(Collections.emptyList());

        // Act
        GetModifyRsuData result = controller.getAllRsus(organization);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getRsuData());
        assertTrue(result.getRsuData().isEmpty());

        verify(rsusRepository, times(1)).findAllDetailedRsuInfoRowsByOrganization(organization);
    }

    @Test
    void testGetSingleRsuData_success() {
        // Arrange
        String organization = "CDOT";
        String rsuIp = "192.168.1.1";
        String username = "testuser@example.com";

        List<RsuDetailedInfoRow> rows = List.of(
                createTestRow(rsuIp, 39.7392, -104.9903, organization));

        ModifyRsuAllowedSelections allowedSelections = new ModifyRsuAllowedSelections();
        allowedSelections.setPrimaryRoutes(List.of("I-25", "I-70"));

        when(rsusRepository.findDetailedRsuInfoRowsByIp(rsuIp)).thenReturn(rows);
        when(rsuManagementService.getAllowedSelections(username)).thenReturn(allowedSelections);

        try (MockedStatic<PermissionService> mockedStatic = Mockito.mockStatic(PermissionService.class)) {
            mockedStatic.when(() -> PermissionService.getUsername(any())).thenReturn(username);

            // Act
            GetModifyRsuDataSingle result = controller.getSingleRsuData(organization, rsuIp);

            // Assert
            assertNotNull(result);
            assertNotNull(result.getRsuData());
            assertEquals(rsuIp, result.getRsuData().getIp());
            assertNotNull(result.getAllowedSelections());
            assertEquals(2, result.getAllowedSelections().getPrimaryRoutes().size());

            verify(rsusRepository, times(1)).findDetailedRsuInfoRowsByIp(rsuIp);
            verify(rsuManagementService, times(1)).getAllowedSelections(username);
        }
    }

    @Test
    void testGetSingleRsuData_multipleOrganizations() {
        // Arrange
        String organization = "CDOT";
        String rsuIp = "192.168.1.1";
        String username = "testuser@example.com";

        List<RsuDetailedInfoRow> rows = Arrays.asList(
                createTestRow(rsuIp, 39.7392, -104.9903, "CDOT"),
                createTestRow(rsuIp, 39.7392, -104.9903, "City of Denver"));

        ModifyRsuAllowedSelections allowedSelections = new ModifyRsuAllowedSelections();

        when(rsusRepository.findDetailedRsuInfoRowsByIp(rsuIp)).thenReturn(rows);
        when(rsuManagementService.getAllowedSelections(username)).thenReturn(allowedSelections);

        try (MockedStatic<PermissionService> mockedStatic = Mockito.mockStatic(PermissionService.class)) {
            mockedStatic.when(() -> PermissionService.getUsername(any())).thenReturn(username);

            // Act
            GetModifyRsuDataSingle result = controller.getSingleRsuData(organization, rsuIp);

            // Assert
            assertNotNull(result);
            assertEquals(2, result.getRsuData().getOrganizations().size());
            assertTrue(result.getRsuData().getOrganizations().contains("CDOT"));
            assertTrue(result.getRsuData().getOrganizations().contains("City of Denver"));
        }
    }

    @Test
    void testGetSingleRsuData_notFound() {
        // Arrange
        String organization = "CDOT";
        String rsuIp = "192.168.1.99";

        when(rsusRepository.findDetailedRsuInfoRowsByIp(rsuIp)).thenReturn(Collections.emptyList());

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.getSingleRsuData(organization, rsuIp));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertThat(exception.getReason()).contains("RSU not found");

        verify(rsusRepository, times(1)).findDetailedRsuInfoRowsByIp(rsuIp);
        verifyNoInteractions(rsuManagementService);
    }

    @Test
    void testGetSingleRsuData_multipleRsusWithSameIp() {
        // Arrange - This shouldn't happen, but tests the error handling
        String organization = "CDOT";
        String rsuIp = "192.168.1.1";

        List<RsuDetailedInfoRow> rows = Arrays.asList(
                createTestRow(rsuIp, 39.7392, -104.9903, "CDOT"),
                createTestRow("192.168.1.2", 39.7500, -105.0000, "City of Denver") // Different IP
        );

        when(rsusRepository.findDetailedRsuInfoRowsByIp(rsuIp)).thenReturn(rows);

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.getSingleRsuData(organization, rsuIp));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        assertThat(exception.getReason()).contains("Multiple RSUs found with the same IP address");

        verify(rsusRepository, times(1)).findDetailedRsuInfoRowsByIp(rsuIp);
        verifyNoInteractions(rsuManagementService);
    }

    @Test
    void testGetAllRsus_aggregatesOrganizationsCorrectly() {
        // Arrange - Multiple RSUs with overlapping organizations
        String organization = "CDOT";
        List<RsuDetailedInfoRow> rows = Arrays.asList(
                createTestRow("192.168.1.1", 39.7392, -104.9903, "CDOT"),
                createTestRow("192.168.1.1", 39.7392, -104.9903, "RTD"),
                createTestRow("192.168.1.2", 39.7500, -105.0000, "CDOT"),
                createTestRow("192.168.1.2", 39.7500, -105.0000, "City of Denver"));

        when(rsusRepository.findAllDetailedRsuInfoRowsByOrganization(organization)).thenReturn(rows);

        // Act
        GetModifyRsuData result = controller.getAllRsus(organization);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getRsuData().size());

        // Find RSU 192.168.1.1
        var rsu1 = result.getRsuData().stream()
                .filter(r -> r.getIp().equals("192.168.1.1"))
                .findFirst()
                .orElseThrow();
        assertEquals(2, rsu1.getOrganizations().size());
        assertTrue(rsu1.getOrganizations().contains("CDOT"));
        assertTrue(rsu1.getOrganizations().contains("RTD"));

        // Find RSU 192.168.1.2
        var rsu2 = result.getRsuData().stream()
                .filter(r -> r.getIp().equals("192.168.1.2"))
                .findFirst()
                .orElseThrow();
        assertEquals(2, rsu2.getOrganizations().size());
        assertTrue(rsu2.getOrganizations().contains("CDOT"));
        assertTrue(rsu2.getOrganizations().contains("City of Denver"));
    }

    @Test
    void testGetSingleRsuData_allFieldsPopulated() {
        // Arrange
        String organization = "CDOT";
        String rsuIp = "192.168.1.1";
        String username = "testuser@example.com";

        RsuDetailedInfoRow row = createTestRow(rsuIp, 39.7392, -104.9903, organization);
        List<RsuDetailedInfoRow> rows = List.of(row);

        ModifyRsuAllowedSelections allowedSelections = new ModifyRsuAllowedSelections();
        allowedSelections.setPrimaryRoutes(List.of("I-25"));
        allowedSelections.setRsuModels(List.of("Commsignia ITS-RS4-M"));
        allowedSelections.setSshCredentialGroups(List.of("ssh_group1"));
        allowedSelections.setSnmpCredentialGroups(List.of("snmp_group1"));
        allowedSelections.setSnmpVersionGroups(List.of("41"));
        allowedSelections.setOrganizations(List.of("CDOT"));

        when(rsusRepository.findDetailedRsuInfoRowsByIp(rsuIp)).thenReturn(rows);
        when(rsuManagementService.getAllowedSelections(username)).thenReturn(allowedSelections);

        try (MockedStatic<PermissionService> mockedStatic = Mockito.mockStatic(PermissionService.class)) {
            mockedStatic.when(() -> PermissionService.getUsername(any())).thenReturn(username);

            // Act
            GetModifyRsuDataSingle result = controller.getSingleRsuData(organization, rsuIp);

            // Assert
            assertNotNull(result.getRsuData());
            assertEquals(rsuIp, result.getRsuData().getIp());
            assertEquals(100.5f, result.getRsuData().getMilepost());
            assertEquals("I-25", result.getRsuData().getPrimaryRoute());
            assertEquals("SN12345", result.getRsuData().getSerialNumber());
            assertEquals("SCMS001", result.getRsuData().getScmsId());
            assertEquals("Commsignia ITS-RS4-M", result.getRsuData().getModel());
            assertEquals("ssh_group1", result.getRsuData().getSshCredentialGroup());
            assertEquals("snmp_group1", result.getRsuData().getSnmpCredentialGroup());
            assertEquals("41", result.getRsuData().getSnmpVersionGroup());

            assertNotNull(result.getRsuData().getGeoPosition());
            assertEquals(39.7392, result.getRsuData().getGeoPosition().getLatitude(), 0.0001);
            assertEquals(-104.9903, result.getRsuData().getGeoPosition().getLongitude(), 0.0001);

            assertNotNull(result.getAllowedSelections());
            assertEquals(1, result.getAllowedSelections().getPrimaryRoutes().size());
            assertEquals(1, result.getAllowedSelections().getRsuModels().size());
            assertEquals(1, result.getAllowedSelections().getSshCredentialGroups().size());
        }
    }
}
