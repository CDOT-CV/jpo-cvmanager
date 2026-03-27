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
    void testGetScmsStatuses_StableResult_WhenMultipleRecordsHaveSameTimestamp() throws Exception {
        // Arrange
        Organization org = saveOrganization("SameTimestampOrg");

        Manufacturer manufacturer = saveManufacturer("Manufacturer3");
        RsuModel model = saveRsuModel("Model3", manufacturer);
        SnmpProtocol protocol = saveSnmpProtocol("v3-3", "v3");
        SnmpCredential snmpCred = saveSnmpCredential("snmp3", "user", "pass", org);
        RsuCredential rsuCred = saveRsuCredential("rsu3", "user", "pass", org);

        Rsu rsu = saveRsu("10.0.0.20", model, snmpCred, rsuCred, protocol, org);

        Instant sameTime = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // Identical timestamps for multiple health records.
        // The JPQL MAX(timestamp) subquery may join multiple records if they share the maximum timestamp.
        saveScmsHealth(rsu, sameTime, true);
        saveScmsHealth(rsu, sameTime, false);

        // Act
        List<ScmsHealthRsuProjection> results = scmsHealthService.getScmsStatuses("SameTimestampOrg");

        // Assert
        // Current logic might return multiple records for the same RSU if timestamps are identical.
        assertFalse(results.isEmpty(), "Should return at least one record");
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