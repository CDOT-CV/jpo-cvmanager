package us.dot.its.jpo.ode.api.utils;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import us.dot.its.jpo.ode.api.models.postgres.derived.RsuDetailedInfo;
import us.dot.its.jpo.ode.api.models.postgres.derived.RsuDetailedInfoRow;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RsuAggregationUtilTest {

    private static final GeometryFactory geometryFactory = new GeometryFactory();

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
    void testAggregateRsuRows_singleRsuSingleOrganization() {
        // Arrange
        List<RsuDetailedInfoRow> rows = List.of(
                createTestRow("192.168.1.1", 39.7392, -104.9903, "CDOT"));

        // Act
        Map<String, RsuDetailedInfo> result = RsuAggregationUtil.aggregateRsuRows(rows);

        // Assert
        assertEquals(1, result.size());
        assertTrue(result.containsKey("192.168.1.1"));

        RsuDetailedInfo rsu = result.get("192.168.1.1");
        assertEquals("192.168.1.1", rsu.getIp());
        assertEquals(39.7392, rsu.getGeoPosition().getLatitude(), 0.0001);
        assertEquals(-104.9903, rsu.getGeoPosition().getLongitude(), 0.0001);
        assertEquals(100.5f, rsu.getMilepost());
        assertEquals("I-25", rsu.getPrimaryRoute());
        assertEquals("SN12345", rsu.getSerialNumber());
        assertEquals("SCMS001", rsu.getScmsId());
        assertEquals("Commsignia ITS-RS4-M", rsu.getModel());
        assertEquals("ssh_group1", rsu.getSshCredentialGroup());
        assertEquals("snmp_group1", rsu.getSnmpCredentialGroup());
        assertEquals("41", rsu.getSnmpVersionGroup());
        assertEquals(1, rsu.getOrganizations().size());
        assertEquals("CDOT", rsu.getOrganizations().get(0));
    }

    @Test
    void testAggregateRsuRows_singleRsuMultipleOrganizations() {
        // Arrange
        List<RsuDetailedInfoRow> rows = Arrays.asList(
                createTestRow("192.168.1.1", 39.7392, -104.9903, "CDOT"),
                createTestRow("192.168.1.1", 39.7392, -104.9903, "City of Denver"),
                createTestRow("192.168.1.1", 39.7392, -104.9903, "RTD"));

        // Act
        Map<String, RsuDetailedInfo> result = RsuAggregationUtil.aggregateRsuRows(rows);

        // Assert
        assertEquals(1, result.size());

        RsuDetailedInfo rsu = result.get("192.168.1.1");
        assertEquals("192.168.1.1", rsu.getIp());
        assertEquals(3, rsu.getOrganizations().size());
        assertEquals("CDOT", rsu.getOrganizations().get(0));
        assertEquals("City of Denver", rsu.getOrganizations().get(1));
        assertEquals("RTD", rsu.getOrganizations().get(2));
    }

    @Test
    void testAggregateRsuRows_multipleRsusSingleOrganizationEach() {
        // Arrange
        List<RsuDetailedInfoRow> rows = Arrays.asList(
                createTestRow("192.168.1.1", 39.7392, -104.9903, "CDOT"),
                createTestRow("192.168.1.2", 39.7500, -105.0000, "City of Denver"),
                createTestRow("192.168.1.3", 39.7600, -105.0100, "RTD"));

        // Act
        Map<String, RsuDetailedInfo> result = RsuAggregationUtil.aggregateRsuRows(rows);

        // Assert
        assertEquals(3, result.size());
        assertTrue(result.containsKey("192.168.1.1"));
        assertTrue(result.containsKey("192.168.1.2"));
        assertTrue(result.containsKey("192.168.1.3"));

        assertEquals("CDOT", result.get("192.168.1.1").getOrganizations().get(0));
        assertEquals("City of Denver", result.get("192.168.1.2").getOrganizations().get(0));
        assertEquals("RTD", result.get("192.168.1.3").getOrganizations().get(0));
    }

    @Test
    void testAggregateRsuRows_multipleRsusMultipleOrganizations() {
        // Arrange
        List<RsuDetailedInfoRow> rows = Arrays.asList(
                createTestRow("192.168.1.1", 39.7392, -104.9903, "CDOT"),
                createTestRow("192.168.1.1", 39.7392, -104.9903, "City of Denver"),
                createTestRow("192.168.1.2", 39.7500, -105.0000, "RTD"),
                createTestRow("192.168.1.2", 39.7500, -105.0000, "CDOT"));

        // Act
        Map<String, RsuDetailedInfo> result = RsuAggregationUtil.aggregateRsuRows(rows);

        // Assert
        assertEquals(2, result.size());

        RsuDetailedInfo rsu1 = result.get("192.168.1.1");
        assertEquals(2, rsu1.getOrganizations().size());
        assertTrue(rsu1.getOrganizations().contains("CDOT"));
        assertTrue(rsu1.getOrganizations().contains("City of Denver"));

        RsuDetailedInfo rsu2 = result.get("192.168.1.2");
        assertEquals(2, rsu2.getOrganizations().size());
        assertTrue(rsu2.getOrganizations().contains("RTD"));
        assertTrue(rsu2.getOrganizations().contains("CDOT"));
    }

    @Test
    void testAggregateRsuRows_emptyList() {
        // Arrange
        List<RsuDetailedInfoRow> rows = Collections.emptyList();

        // Act
        Map<String, RsuDetailedInfo> result = RsuAggregationUtil.aggregateRsuRows(rows);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testAggregateRsuRows_maintainsInsertionOrder() {
        // Arrange - Create rows with IPs in specific order
        List<RsuDetailedInfoRow> rows = Arrays.asList(
                createTestRow("192.168.1.3", 39.7392, -104.9903, "Org1"),
                createTestRow("192.168.1.1", 39.7500, -105.0000, "Org2"),
                createTestRow("192.168.1.2", 39.7600, -105.0100, "Org3"));

        // Act
        Map<String, RsuDetailedInfo> result = RsuAggregationUtil.aggregateRsuRows(rows);

        // Assert - LinkedHashMap should maintain insertion order
        List<String> keys = new ArrayList<>(result.keySet());
        assertEquals("192.168.1.3", keys.get(0));
        assertEquals("192.168.1.1", keys.get(1));
        assertEquals("192.168.1.2", keys.get(2));
    }

    @Test
    void testAggregateRsuRows_organizationOrder() {
        // Arrange - Organizations should be added in the order they appear
        List<RsuDetailedInfoRow> rows = Arrays.asList(
                createTestRow("192.168.1.1", 39.7392, -104.9903, "Org3"),
                createTestRow("192.168.1.1", 39.7392, -104.9903, "Org1"),
                createTestRow("192.168.1.1", 39.7392, -104.9903, "Org2"));

        // Act
        Map<String, RsuDetailedInfo> result = RsuAggregationUtil.aggregateRsuRows(rows);

        // Assert - Organizations should be in order they were encountered
        List<String> orgs = result.get("192.168.1.1").getOrganizations();
        assertEquals("Org3", orgs.get(0));
        assertEquals("Org1", orgs.get(1));
        assertEquals("Org2", orgs.get(2));
    }

    @Test
    void testAggregateRsuRowsToList_singleRsu() {
        // Arrange
        List<RsuDetailedInfoRow> rows = List.of(
                createTestRow("192.168.1.1", 39.7392, -104.9903, "CDOT"));

        // Act
        List<RsuDetailedInfo> result = RsuAggregationUtil.aggregateRsuRowsToList(rows);

        // Assert
        assertEquals(1, result.size());
        assertEquals("192.168.1.1", result.get(0).getIp());
        assertEquals("CDOT", result.get(0).getOrganizations().get(0));
    }

    @Test
    void testAggregateRsuRowsToList_multipleRsus() {
        // Arrange
        List<RsuDetailedInfoRow> rows = Arrays.asList(
                createTestRow("192.168.1.1", 39.7392, -104.9903, "CDOT"),
                createTestRow("192.168.1.2", 39.7500, -105.0000, "City of Denver"),
                createTestRow("192.168.1.3", 39.7600, -105.0100, "RTD"));

        // Act
        List<RsuDetailedInfo> result = RsuAggregationUtil.aggregateRsuRowsToList(rows);

        // Assert
        assertEquals(3, result.size());

        // Find each RSU in the list (order from LinkedHashMap)
        Optional<RsuDetailedInfo> rsu1 = result.stream()
                .filter(r -> r.getIp().equals("192.168.1.1")).findFirst();
        Optional<RsuDetailedInfo> rsu2 = result.stream()
                .filter(r -> r.getIp().equals("192.168.1.2")).findFirst();
        Optional<RsuDetailedInfo> rsu3 = result.stream()
                .filter(r -> r.getIp().equals("192.168.1.3")).findFirst();

        assertTrue(rsu1.isPresent());
        assertTrue(rsu2.isPresent());
        assertTrue(rsu3.isPresent());
    }

    @Test
    void testAggregateRsuRowsToList_emptyList() {
        // Arrange
        List<RsuDetailedInfoRow> rows = Collections.emptyList();

        // Act
        List<RsuDetailedInfo> result = RsuAggregationUtil.aggregateRsuRowsToList(rows);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testAggregateRsuRowsToList_aggregatesOrganizations() {
        // Arrange
        List<RsuDetailedInfoRow> rows = Arrays.asList(
                createTestRow("192.168.1.1", 39.7392, -104.9903, "CDOT"),
                createTestRow("192.168.1.1", 39.7392, -104.9903, "City of Denver"));

        // Act
        List<RsuDetailedInfo> result = RsuAggregationUtil.aggregateRsuRowsToList(rows);

        // Assert
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getOrganizations().size());
    }

    @Test
    void testAggregateRsuRowsToSingle_singleRow() {
        // Arrange
        List<RsuDetailedInfoRow> rows = List.of(
                createTestRow("192.168.1.1", 39.7392, -104.9903, "CDOT"));

        // Act
        RsuDetailedInfo result = RsuAggregationUtil.aggregateRsuRowsToSingle(rows);

        // Assert
        assertNotNull(result);
        assertEquals("192.168.1.1", result.getIp());
        assertEquals(1, result.getOrganizations().size());
        assertEquals("CDOT", result.getOrganizations().get(0));
    }

    @Test
    void testAggregateRsuRowsToSingle_multipleRowsSameRsu() {
        // Arrange - Multiple rows for same RSU with different organizations
        List<RsuDetailedInfoRow> rows = Arrays.asList(
                createTestRow("192.168.1.1", 39.7392, -104.9903, "CDOT"),
                createTestRow("192.168.1.1", 39.7392, -104.9903, "City of Denver"),
                createTestRow("192.168.1.1", 39.7392, -104.9903, "RTD"));

        // Act
        RsuDetailedInfo result = RsuAggregationUtil.aggregateRsuRowsToSingle(rows);

        // Assert
        assertNotNull(result);
        assertEquals("192.168.1.1", result.getIp());
        assertEquals(3, result.getOrganizations().size());
    }

    @Test
    void testAggregateRsuRowsToSingle_multipleRsus_returnsFirst() {
        // Arrange - Multiple RSUs, should return the first one (by insertion order)
        List<RsuDetailedInfoRow> rows = Arrays.asList(
                createTestRow("192.168.1.1", 39.7392, -104.9903, "CDOT"),
                createTestRow("192.168.1.2", 39.7500, -105.0000, "City of Denver"));

        // Act
        RsuDetailedInfo result = RsuAggregationUtil.aggregateRsuRowsToSingle(rows);

        // Assert
        assertNotNull(result);
        assertEquals("192.168.1.1", result.getIp());
    }

    @Test
    void testAggregateRsuRowsToSingle_emptyList() {
        // Arrange
        List<RsuDetailedInfoRow> rows = Collections.emptyList();

        // Act
        RsuDetailedInfo result = RsuAggregationUtil.aggregateRsuRowsToSingle(rows);

        // Assert
        assertNull(result);
    }

    @Test
    void testAggregateRsuRows_geometryCoordinateExtraction() {
        // Arrange - Test specific lat/lon values
        RsuDetailedInfoRow row = createTestRow("192.168.1.1", 40.0150, -105.2705, "CDOT");

        // Act
        Map<String, RsuDetailedInfo> result = RsuAggregationUtil.aggregateRsuRows(List.of(row));

        // Assert
        RsuDetailedInfo rsu = result.get("192.168.1.1");
        assertNotNull(rsu.getGeoPosition());
        assertEquals(40.0150, rsu.getGeoPosition().getLatitude(), 0.0001);
        assertEquals(-105.2705, rsu.getGeoPosition().getLongitude(), 0.0001);
    }

    @Test
    void testAggregateRsuRows_allFieldsPopulated() {
        // Arrange
        RsuDetailedInfoRow row = new RsuDetailedInfoRow();
        row.setIpv4Address("10.0.0.1");
        row.setGeometry(geometryFactory.createPoint(new Coordinate(-106.5, 38.5)));
        row.setMilepost(250.75f);
        row.setPrimaryRoute("US-50");
        row.setSerialNumber("TEST-SN-999");
        row.setIssScmsId("SCMS-TEST-999");
        row.setModel("Test Model ABC");
        row.setSshCredential("test_ssh");
        row.setSnmpCredential("test_snmp");
        row.setSnmpVersion("1218");
        row.setOrgName("Test Org");

        // Act
        Map<String, RsuDetailedInfo> result = RsuAggregationUtil.aggregateRsuRows(List.of(row));

        // Assert
        RsuDetailedInfo rsu = result.get("10.0.0.1");
        assertEquals("10.0.0.1", rsu.getIp());
        assertEquals(38.5, rsu.getGeoPosition().getLatitude());
        assertEquals(-106.5, rsu.getGeoPosition().getLongitude());
        assertEquals(250.75f, rsu.getMilepost());
        assertEquals("US-50", rsu.getPrimaryRoute());
        assertEquals("TEST-SN-999", rsu.getSerialNumber());
        assertEquals("SCMS-TEST-999", rsu.getScmsId());
        assertEquals("Test Model ABC", rsu.getModel());
        assertEquals("test_ssh", rsu.getSshCredentialGroup());
        assertEquals("test_snmp", rsu.getSnmpCredentialGroup());
        assertEquals("1218", rsu.getSnmpVersionGroup());
        assertEquals(List.of("Test Org"), rsu.getOrganizations());
    }
}
