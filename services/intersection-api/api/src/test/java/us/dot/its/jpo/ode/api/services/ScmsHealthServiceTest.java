package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.postgres.tables.*;
import us.dot.its.jpo.ode.api.repositories.*;

import java.net.InetAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import us.dot.its.jpo.ode.api.models.postgres.projections.ScmsHealthRsuProjection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
@Transactional
class ScmsHealthServiceTest {

    @Autowired
    private ScmsHealthService scmsHealthService;

    @Autowired
    private ScmsHealthRepository scmsHealthRepository;

    @Autowired
    private RsuRepository rsuRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RsuOrganizationRepository rsuOrganizationRepository;

    @Autowired
    private ManufacturerRepository manufacturerRepository;

    @Autowired
    private RsuModelRepository rsuModelRepository;

    @Autowired
    private RsuCredentialRepository rsuCredentialRepository;

    @Autowired
    private SnmpCredentialRepository snmpCredentialRepository;

    @Autowired
    private SnmpProtocolRepository snmpProtocolRepository;

    @BeforeEach
    void setUp() {
        scmsHealthRepository.deleteAll();
        rsuOrganizationRepository.deleteAll();
        rsuRepository.deleteAll();
        organizationRepository.deleteAll();
        rsuModelRepository.deleteAll();
        manufacturerRepository.deleteAll();
        rsuCredentialRepository.deleteAll();
        snmpCredentialRepository.deleteAll();
        snmpProtocolRepository.deleteAll();
    }

    @Test
    void testGetScmsStatuses_ReturnsLatestForEachRsuInOrganization() throws Exception {
        // Arrange
        Organization org1 = saveOrganization("Org1");
        Organization org2 = saveOrganization("Org2");

        Manufacturer manufacturer = saveManufacturer("Manufacturer1");
        RsuModel model = saveRsuModel("Model1", manufacturer);
        SnmpProtocol protocol = saveSnmpProtocol("v3", "v3");
        SnmpCredential snmpCred = saveSnmpCredential("snmp", "user", "pass", org1);
        RsuCredential rsuCred = saveRsuCredential("rsu", "user", "pass", org1);

        Rsu rsu1 = saveRsu("10.0.0.1", model, snmpCred, rsuCred, protocol, org1);
        Rsu rsu2 = saveRsu("10.0.0.2", model, snmpCred, rsuCred, protocol, org1);
        Rsu rsu3 = saveRsu("10.0.0.3", model, snmpCred, rsuCred, protocol, org2);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant older = now.minus(1, ChronoUnit.HOURS);

        // SCMS Health records
        saveScmsHealth(rsu1, older, true);
        ScmsHealth rsu1Latest = saveScmsHealth(rsu1, now, false); // Latest for RSU 1

        // Single health record for RSU 2
        ScmsHealth rsu2Latest = saveScmsHealth(rsu2, now, true);

        // Health record for RSU 3 (Org 2)
        saveScmsHealth(rsu3, now, true);

        // Act
        List<ScmsHealthRsuProjection> results = scmsHealthService.getScmsStatuses("Org1");

        // Assert
        assertEquals(2, results.size(), "Should return 2 records for Org1");
        
        ScmsHealthRsuProjection result1 = results.stream().filter(res -> res.getRsu().getIpv4Address().getHostAddress().equals("10.0.0.1")).findFirst().orElseThrow();
        assertEquals(rsu1Latest.getHealth(), result1.getScmsHealth().getHealth());

        ScmsHealthRsuProjection result2 = results.stream().filter(res -> res.getRsu().getIpv4Address().getHostAddress().equals("10.0.0.2")).findFirst().orElseThrow();
        assertEquals(rsu2Latest.getHealth(), result2.getScmsHealth().getHealth());
    }

    @Test
    void testGetScmsStatuses_ReturnsEmpty_WhenOrganizationHasNoRsus() {
        // Arrange
        saveOrganization("EmptyOrg");

        // Act
        List<ScmsHealthRsuProjection> results = scmsHealthService.getScmsStatuses("EmptyOrg");

        // Assert
        assertTrue(results.isEmpty(), "Should return an empty list for an organization with no RSUs");
    }

    @Test
    void testGetScmsStatuses_ReturnsNullValue_WhenOrganizationHasRsusButNoHealthRecords() throws Exception {
        // Arrange
        Organization org = saveOrganization("NoHealthOrg");

        Manufacturer manufacturer = saveManufacturer("Manufacturer2");
        RsuModel model = saveRsuModel("Model2", manufacturer);
        SnmpProtocol protocol = saveSnmpProtocol("v3-2", "v3");
        SnmpCredential snmpCred = saveSnmpCredential("snmp2", "user", "pass", org);
        RsuCredential rsuCred = saveRsuCredential("rsu2", "user", "pass", org);

        saveRsu("10.0.0.10", model, snmpCred, rsuCred, protocol, org);

        // Act
        List<ScmsHealthRsuProjection> results = scmsHealthService.getScmsStatuses("NoHealthOrg");

        // Assert
        assertEquals(1, results.size(), "Should return 1 entry even when RSU has no health records");
        assertNull(results.get(0).getScmsHealth(), "The ScmsHealth object should be null when the RSU has no health records");
    }

    @Test
    void testGetScmsStatuses_ReturnsEmpty_WhenOrganizationDoesNotExist() {
        // Act
        List<ScmsHealthRsuProjection> results = scmsHealthService.getScmsStatuses("NonExistentOrg");

        // Assert
        assertTrue(results.isEmpty(), "Should return an empty list for a non-existent organization");
    }

    @Test
    void testGetScmsStatuses_ReturnsExactlyOneRowPerRsu_WhenMultipleRecordsHaveSameTimestamp() throws Exception {
        // Arrange
        Organization org = saveOrganization("SameTimestampOrg");

        Manufacturer manufacturer = saveManufacturer("Manufacturer3");
        RsuModel model = saveRsuModel("Model3", manufacturer);
        SnmpProtocol protocol = saveSnmpProtocol("v3-3", "v3");
        SnmpCredential snmpCred = saveSnmpCredential("snmp3", "user", "pass", org);
        RsuCredential rsuCred = saveRsuCredential("rsu3", "user", "pass", org);

        Rsu rsu = saveRsu("10.0.0.20", model, snmpCred, rsuCred, protocol, org);

        Instant sameTime = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // Create multiple health records with identical timestamps.
        // The query must return exactly one row per RSU (matching legacy ROW_NUMBER behavior).
        // The tie-breaker is the highest ID, so the last saved record should be selected.
        ScmsHealth firstRecord = saveScmsHealth(rsu, sameTime, true);
        ScmsHealth secondRecord = saveScmsHealth(rsu, sameTime, false);

        // Verify secondRecord has a higher ID (saved later)
        assertTrue(secondRecord.getId() > firstRecord.getId(),
            "Second record should have higher ID than first record");

        // Act
        List<ScmsHealthRsuProjection> results = scmsHealthService.getScmsStatuses("SameTimestampOrg");

        // Assert
        assertEquals(1, results.size(),
            "Should return exactly one record per RSU even when multiple records have the same timestamp");

        ScmsHealthRsuProjection result = results.get(0);
        assertNotNull(result.getScmsHealth(), "ScmsHealth should not be null");
        assertEquals(secondRecord.getId(), result.getScmsHealth().getId(),
            "Should select the record with the highest ID as a deterministic tie-breaker");
        assertEquals(secondRecord.getHealth(), result.getScmsHealth().getHealth(),
            "Should return the health value from the record with the highest ID");
    }

    @Test
    void testGetScmsStatuses_ReturnsExactlyOneRowPerRsu_WhenMoreThanTwoRecordsHaveSameTimestamp() throws Exception {
        // Arrange - Tests that even with many tied timestamps, exactly one row is returned
        Organization org = saveOrganization("ManyTiesOrg");

        Manufacturer manufacturer = saveManufacturer("Manufacturer4");
        RsuModel model = saveRsuModel("Model4", manufacturer);
        SnmpProtocol protocol = saveSnmpProtocol("v3-4", "v3");
        SnmpCredential snmpCred = saveSnmpCredential("snmp4", "user", "pass", org);
        RsuCredential rsuCred = saveRsuCredential("rsu4", "user", "pass", org);

        Rsu rsu = saveRsu("10.0.0.30", model, snmpCred, rsuCred, protocol, org);

        Instant sameTime = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // Create 5 health records with identical timestamps
        saveScmsHealth(rsu, sameTime, true);
        saveScmsHealth(rsu, sameTime, false);
        saveScmsHealth(rsu, sameTime, true);
        saveScmsHealth(rsu, sameTime, false);
        ScmsHealth lastRecord = saveScmsHealth(rsu, sameTime, true);

        // Act
        List<ScmsHealthRsuProjection> results = scmsHealthService.getScmsStatuses("ManyTiesOrg");

        // Assert
        assertEquals(1, results.size(),
            "Should return exactly one record per RSU even with many timestamp ties");
        assertEquals(lastRecord.getId(), results.get(0).getScmsHealth().getId(),
            "Should select the record with the highest ID");
    }

    @Test
    void testGetScmsStatuses_ResultsOrderedByIpv4Address() throws Exception {
        // Arrange - Legacy query orders by rd.ipv4_address
        Organization org = saveOrganization("OrderedOrg");

        Manufacturer manufacturer = saveManufacturer("Manufacturer5");
        RsuModel model = saveRsuModel("Model5", manufacturer);
        SnmpProtocol protocol = saveSnmpProtocol("v3-5", "v3");
        SnmpCredential snmpCred = saveSnmpCredential("snmp5", "user", "pass", org);
        RsuCredential rsuCred = saveRsuCredential("rsu5", "user", "pass", org);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // Create RSUs in non-sorted order
        Rsu rsu3 = saveRsu("10.0.0.103", model, snmpCred, rsuCred, protocol, org);
        Rsu rsu1 = saveRsu("10.0.0.101", model, snmpCred, rsuCred, protocol, org);
        Rsu rsu2 = saveRsu("10.0.0.102", model, snmpCred, rsuCred, protocol, org);

        saveScmsHealth(rsu3, now, true);
        saveScmsHealth(rsu1, now, true);
        saveScmsHealth(rsu2, now, true);

        // Act
        List<ScmsHealthRsuProjection> results = scmsHealthService.getScmsStatuses("OrderedOrg");

        // Assert - Results should be sorted by IPv4 address
        assertEquals(3, results.size());
        assertEquals("10.0.0.101", results.get(0).getRsu().getIpv4Address().getHostAddress(),
            "First result should be 10.0.0.101");
        assertEquals("10.0.0.102", results.get(1).getRsu().getIpv4Address().getHostAddress(),
            "Second result should be 10.0.0.102");
        assertEquals("10.0.0.103", results.get(2).getRsu().getIpv4Address().getHostAddress(),
            "Third result should be 10.0.0.103");
    }

    @Test
    void testGetScmsStatuses_RsuInMultipleOrganizations_AppearsInEachOrgQuery() throws Exception {
        // Arrange - An RSU can belong to multiple organizations
        Organization org1 = saveOrganization("MultiOrg1");
        Organization org2 = saveOrganization("MultiOrg2");

        Manufacturer manufacturer = saveManufacturer("Manufacturer6");
        RsuModel model = saveRsuModel("Model6", manufacturer);
        SnmpProtocol protocol = saveSnmpProtocol("v3-6", "v3");
        SnmpCredential snmpCred = saveSnmpCredential("snmp6", "user", "pass", org1);
        RsuCredential rsuCred = saveRsuCredential("rsu6", "user", "pass", org1);

        // Create RSU and associate with both organizations
        Rsu sharedRsu = saveRsuWithoutOrg("10.0.0.50", model, snmpCred, rsuCred, protocol);
        saveRsuOrganization(sharedRsu, org1);
        saveRsuOrganization(sharedRsu, org2);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ScmsHealth healthRecord = saveScmsHealth(sharedRsu, now, true);

        // Act
        List<ScmsHealthRsuProjection> resultsOrg1 = scmsHealthService.getScmsStatuses("MultiOrg1");
        List<ScmsHealthRsuProjection> resultsOrg2 = scmsHealthService.getScmsStatuses("MultiOrg2");

        // Assert - RSU should appear in both organization queries
        assertEquals(1, resultsOrg1.size(), "RSU should appear in MultiOrg1 results");
        assertEquals(1, resultsOrg2.size(), "RSU should appear in MultiOrg2 results");
        assertEquals(healthRecord.getId(), resultsOrg1.get(0).getScmsHealth().getId());
        assertEquals(healthRecord.getId(), resultsOrg2.get(0).getScmsHealth().getId());
    }

    @Test
    void testGetScmsStatuses_MixedRsusWithAndWithoutHealthRecords() throws Exception {
        // Arrange - Legacy query uses LEFT JOIN so RSUs without health records are included
        Organization org = saveOrganization("MixedOrg");

        Manufacturer manufacturer = saveManufacturer("Manufacturer7");
        RsuModel model = saveRsuModel("Model7", manufacturer);
        SnmpProtocol protocol = saveSnmpProtocol("v3-7", "v3");
        SnmpCredential snmpCred = saveSnmpCredential("snmp7", "user", "pass", org);
        RsuCredential rsuCred = saveRsuCredential("rsu7", "user", "pass", org);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // RSU with health records
        Rsu rsuWithHealth = saveRsu("10.0.0.60", model, snmpCred, rsuCred, protocol, org);
        ScmsHealth healthRecord = saveScmsHealth(rsuWithHealth, now, true);

        // RSU without health records
        Rsu rsuWithoutHealth = saveRsu("10.0.0.61", model, snmpCred, rsuCred, protocol, org);

        // Act
        List<ScmsHealthRsuProjection> results = scmsHealthService.getScmsStatuses("MixedOrg");

        // Assert - Both RSUs should be returned, sorted by IP
        assertEquals(2, results.size(), "Should return both RSUs");

        // First RSU (10.0.0.60) has health record
        assertEquals("10.0.0.60", results.get(0).getRsu().getIpv4Address().getHostAddress());
        assertNotNull(results.get(0).getScmsHealth(), "First RSU should have health record");
        assertEquals(healthRecord.getId(), results.get(0).getScmsHealth().getId());

        // Second RSU (10.0.0.61) has no health record
        assertEquals("10.0.0.61", results.get(1).getRsu().getIpv4Address().getHostAddress());
        assertNull(results.get(1).getScmsHealth(), "Second RSU should have null health record");
    }

    @Test
    void testGetScmsStatuses_DeterministicResults_MultipleCallsReturnSameOrder() throws Exception {
        // Arrange - Ensure the query returns deterministic results on repeated calls
        Organization org = saveOrganization("DeterministicOrg");

        Manufacturer manufacturer = saveManufacturer("Manufacturer8");
        RsuModel model = saveRsuModel("Model8", manufacturer);
        SnmpProtocol protocol = saveSnmpProtocol("v3-8", "v3");
        SnmpCredential snmpCred = saveSnmpCredential("snmp8", "user", "pass", org);
        RsuCredential rsuCred = saveRsuCredential("rsu8", "user", "pass", org);

        Instant sameTime = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Rsu rsu1 = saveRsu("10.0.0.70", model, snmpCred, rsuCred, protocol, org);
        Rsu rsu2 = saveRsu("10.0.0.71", model, snmpCred, rsuCred, protocol, org);

        // Create multiple records with same timestamp for each RSU
        saveScmsHealth(rsu1, sameTime, true);
        ScmsHealth rsu1Latest = saveScmsHealth(rsu1, sameTime, false);
        saveScmsHealth(rsu2, sameTime, false);
        ScmsHealth rsu2Latest = saveScmsHealth(rsu2, sameTime, true);

        // Act - Call multiple times
        List<ScmsHealthRsuProjection> results1 = scmsHealthService.getScmsStatuses("DeterministicOrg");
        List<ScmsHealthRsuProjection> results2 = scmsHealthService.getScmsStatuses("DeterministicOrg");
        List<ScmsHealthRsuProjection> results3 = scmsHealthService.getScmsStatuses("DeterministicOrg");

        // Assert - All calls should return identical results
        assertEquals(2, results1.size());
        assertEquals(2, results2.size());
        assertEquals(2, results3.size());

        // Verify same IDs are returned each time
        assertEquals(results1.get(0).getScmsHealth().getId(), results2.get(0).getScmsHealth().getId());
        assertEquals(results1.get(0).getScmsHealth().getId(), results3.get(0).getScmsHealth().getId());
        assertEquals(results1.get(1).getScmsHealth().getId(), results2.get(1).getScmsHealth().getId());
        assertEquals(results1.get(1).getScmsHealth().getId(), results3.get(1).getScmsHealth().getId());

        // Verify correct records are selected (highest ID per RSU)
        assertEquals(rsu1Latest.getId(), results1.get(0).getScmsHealth().getId());
        assertEquals(rsu2Latest.getId(), results1.get(1).getScmsHealth().getId());
    }

    @Test
    void testGetScmsStatuses_SelectsLatestTimestamp_NotHighestId() throws Exception {
        // Arrange - Ensure timestamp takes precedence over ID for selecting the latest record
        Organization org = saveOrganization("TimestampPrecedenceOrg");

        Manufacturer manufacturer = saveManufacturer("Manufacturer9");
        RsuModel model = saveRsuModel("Model9", manufacturer);
        SnmpProtocol protocol = saveSnmpProtocol("v3-9", "v3");
        SnmpCredential snmpCred = saveSnmpCredential("snmp9", "user", "pass", org);
        RsuCredential rsuCred = saveRsuCredential("rsu9", "user", "pass", org);

        Rsu rsu = saveRsu("10.0.0.80", model, snmpCred, rsuCred, protocol, org);

        Instant older = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        Instant newer = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // Save older record first (will have lower ID)
        saveScmsHealth(rsu, older, false);
        // Save newer record second (will have higher ID AND newer timestamp)
        ScmsHealth newerRecord = saveScmsHealth(rsu, newer, true);
        // Save another older record last (will have highest ID but older timestamp)
        saveScmsHealth(rsu, older, false);

        // Act
        List<ScmsHealthRsuProjection> results = scmsHealthService.getScmsStatuses("TimestampPrecedenceOrg");

        // Assert - Should select the record with the newest timestamp, not the highest ID
        assertEquals(1, results.size());
        assertEquals(newerRecord.getId(), results.get(0).getScmsHealth().getId(),
            "Should select record with newest timestamp, not highest ID");
        assertEquals(newerRecord.getHealth(), results.get(0).getScmsHealth().getHealth());
    }

    private Organization saveOrganization(String name) {
        Organization org = new Organization();
        org.setName(name);
        return organizationRepository.save(org);
    }

    private Manufacturer saveManufacturer(String name) {
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName(name);
        return manufacturerRepository.save(manufacturer);
    }

    private RsuModel saveRsuModel(String name, Manufacturer manufacturer) {
        RsuModel model = new RsuModel();
        model.setName(name);
        model.setSupportedRadio("DSRC");
        model.setManufacturer(manufacturer);
        return rsuModelRepository.save(model);
    }

    private SnmpProtocol saveSnmpProtocol(String nickname, String protocolCode) {
        SnmpProtocol protocol = new SnmpProtocol();
        protocol.setNickname(nickname);
        protocol.setProtocolCode(protocolCode);
        return snmpProtocolRepository.save(protocol);
    }

    private SnmpCredential saveSnmpCredential(String nickname, String user, String pass, Organization org) {
        SnmpCredential snmpCred = new SnmpCredential();
        snmpCred.setNickname(nickname);
        snmpCred.setUsername(user);
        snmpCred.setPassword(pass);
        snmpCred.setOwnerOrganization(org);
        return snmpCredentialRepository.save(snmpCred);
    }

    private RsuCredential saveRsuCredential(String nickname, String user, String pass, Organization org) {
        RsuCredential rsuCred = new RsuCredential();
        rsuCred.setNickname(nickname);
        rsuCred.setUsername(user);
        rsuCred.setPassword(pass);
        rsuCred.setOwnerOrganization(org);
        return rsuCredentialRepository.save(rsuCred);
    }

    private Rsu saveRsu(String ip, RsuModel model, SnmpCredential snmpCred, RsuCredential rsuCred, SnmpProtocol protocol, Organization org) throws Exception {
        Rsu rsu = new Rsu();
        rsu.setIpv4Address(InetAddress.getByName(ip));
        rsu.setModel(model);
        rsu.setSnmpCredential(snmpCred);
        rsu.setCredential(rsuCred);
        rsu.setSnmpProtocol(protocol);
        rsu.setSerialNumber("SN-" + ip);
        rsu.setMilepost(100.0);
        rsu.setIssScmsId("ISS-" + ip);
        rsu.setPrimaryRoute("I-25");
        
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        Point point = geometryFactory.createPoint(new Coordinate(0, 0));
        rsu.setGeography(point);
        
        rsu = rsuRepository.save(rsu);
        saveRsuOrganization(rsu, org);
        return rsu;
    }

    private Rsu saveRsuWithoutOrg(String ip, RsuModel model, SnmpCredential snmpCred, RsuCredential rsuCred, SnmpProtocol protocol) throws Exception {
        Rsu rsu = new Rsu();
        rsu.setIpv4Address(InetAddress.getByName(ip));
        rsu.setModel(model);
        rsu.setSnmpCredential(snmpCred);
        rsu.setCredential(rsuCred);
        rsu.setSnmpProtocol(protocol);
        rsu.setSerialNumber("SN-" + ip);
        rsu.setMilepost(100.0);
        rsu.setIssScmsId("ISS-" + ip);
        rsu.setPrimaryRoute("I-25");

        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        Point point = geometryFactory.createPoint(new Coordinate(0, 0));
        rsu.setGeography(point);

        return rsuRepository.save(rsu);
    }

    private void saveRsuOrganization(Rsu rsu, Organization org) {
        RsuOrganization ro = new RsuOrganization();
        ro.setRsu(rsu);
        ro.setOrganization(org);
        rsuOrganizationRepository.save(ro);
    }

    private ScmsHealth saveScmsHealth(Rsu rsu, Instant timestamp, boolean health) {
        ScmsHealth sh = new ScmsHealth();
        sh.setRsu(rsu);
        sh.setTimestamp(timestamp);
        sh.setHealth(health);
        sh.setExpiration(timestamp.plus(30, ChronoUnit.DAYS));
        return scmsHealthRepository.save(sh);
    }
}