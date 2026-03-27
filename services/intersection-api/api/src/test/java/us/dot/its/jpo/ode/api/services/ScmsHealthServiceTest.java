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
        Organization org1 = new Organization();
        org1.setName("Org1");
        org1 = organizationRepository.save(org1);

        Organization org2 = new Organization();
        org2.setName("Org2");
        org2 = organizationRepository.save(org2);

        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName("Manufacturer1");
        manufacturer = manufacturerRepository.save(manufacturer);

        RsuModel model = new RsuModel();
        model.setName("Model1");
        model.setSupportedRadio("DSRC");
        model.setManufacturer(manufacturer);
        model = rsuModelRepository.save(model);

        SnmpProtocol protocol = new SnmpProtocol();
        protocol.setNickname("v3");
        protocol.setProtocolCode("v3");
        protocol = snmpProtocolRepository.save(protocol);

        SnmpCredential snmpCred = new SnmpCredential();
        snmpCred.setNickname("snmp");
        snmpCred.setUsername("user");
        snmpCred.setPassword("pass");
        snmpCred.setOwnerOrganization(org1);
        snmpCred = snmpCredentialRepository.save(snmpCred);

        RsuCredential rsuCred = new RsuCredential();
        rsuCred.setNickname("rsu");
        rsuCred.setUsername("user");
        rsuCred.setPassword("pass");
        rsuCred.setOwnerOrganization(org1);
        rsuCred = rsuCredentialRepository.save(rsuCred);

        Rsu rsu1 = createRsu("10.0.0.1", model, snmpCred, rsuCred, protocol);
        rsu1 = rsuRepository.save(rsu1);

        Rsu rsu2 = createRsu("10.0.0.2", model, snmpCred, rsuCred, protocol);
        rsu2 = rsuRepository.save(rsu2);

        Rsu rsu3 = createRsu("10.0.0.3", model, snmpCred, rsuCred, protocol);
        rsu3 = rsuRepository.save(rsu3);

        // Map RSUs to Organizations
        saveRsuOrganization(rsu1, org1);
        saveRsuOrganization(rsu2, org1);
        saveRsuOrganization(rsu3, org2);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant older = now.minus(1, ChronoUnit.HOURS);

        // SCMS Health data for RSU 1 (Org 1) - multiple entries, should get latest
        saveScmsHealth(rsu1, older, true);
        ScmsHealth rsu1Latest = saveScmsHealth(rsu1, now, false);

        // SCMS Health data for RSU 2 (Org 1) - single entry
        ScmsHealth rsu2Latest = saveScmsHealth(rsu2, now, true);

        // SCMS Health data for RSU 3 (Org 2) - should not be returned for Org 1
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
        Organization org = new Organization();
        org.setName("EmptyOrg");
        organizationRepository.save(org);

        // Act
        List<ScmsHealthRsuProjection> results = scmsHealthService.getScmsStatuses("EmptyOrg");

        // Assert
        assertTrue(results.isEmpty(), "Should return an empty list for an organization with no RSUs");
    }

    @Test
    void testGetScmsStatuses_ReturnsNullValue_WhenOrganizationHasRsusButNoHealthRecords() throws Exception {
        // Arrange
        Organization org = new Organization();
        org.setName("NoHealthOrg");
        org = organizationRepository.save(org);

        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName("Manufacturer2");
        manufacturer = manufacturerRepository.save(manufacturer);

        RsuModel model = new RsuModel();
        model.setName("Model2");
        model.setSupportedRadio("DSRC");
        model.setManufacturer(manufacturer);
        model = rsuModelRepository.save(model);

        SnmpProtocol protocol = new SnmpProtocol();
        protocol.setNickname("v3-2");
        protocol.setProtocolCode("v3");
        protocol = snmpProtocolRepository.save(protocol);

        SnmpCredential snmpCred = new SnmpCredential();
        snmpCred.setNickname("snmp2");
        snmpCred.setUsername("user");
        snmpCred.setPassword("pass");
        snmpCred.setOwnerOrganization(org);
        snmpCred = snmpCredentialRepository.save(snmpCred);

        RsuCredential rsuCred = new RsuCredential();
        rsuCred.setNickname("rsu2");
        rsuCred.setUsername("user");
        rsuCred.setPassword("pass");
        rsuCred.setOwnerOrganization(org);
        rsuCred = rsuCredentialRepository.save(rsuCred);

        Rsu rsu = createRsu("10.0.0.10", model, snmpCred, rsuCred, protocol);
        rsu = rsuRepository.save(rsu);

        saveRsuOrganization(rsu, org);

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

    private Rsu createRsu(String ip, RsuModel model, SnmpCredential snmpCred, RsuCredential rsuCred, SnmpProtocol protocol) throws Exception {
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