package us.dot.its.jpo.ode.api.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;
import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.fixtures.TestFixtures;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpProtocol;
import us.dot.its.jpo.ode.api.repositories.ManufacturerRepository;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuCredentialRepository;
import us.dot.its.jpo.ode.api.repositories.RsuModelRepository;
import us.dot.its.jpo.ode.api.repositories.RsuOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpCredentialRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpProtocolRepository;

/**
 * Checks polygon WKT against PostGIS and the geo-query against a seeded RSU.
 * Uses the shared {@code integration-test} PostGIS container.
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
class RsuGeoQueryServiceIT {
    private static final String ORGANIZATION = "GeoQueryOrg";
    private static final String MANUFACTURER = "GeoQueryMaker";
    private static final String RSU_IP = "10.11.81.12";
    private static final String CLOSED_SAMPLE_RING = "POLYGON((-105.34460908203145 39.724583197251334,"
            + "-105.34666901855489 39.670180083300174,"
            + "-105.25122529296911 39.679162192647944,"
            + "-105.2539718750002 39.72088725644132,"
            + "-105.34460908203145 39.724583197251334))";
    private static final String UNCLOSED_RING = "POLYGON((0 0,1 0,1 1,0 1))";
    private static final String THREE_POSITION_RING = "POLYGON((0 0,1 0,0 0))";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RsuGeoQueryService rsuGeoQueryService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrganizationRepository organizationRepository;

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

    @Autowired
    private RsuRepository rsuRepository;

    @Autowired
    private RsuOrganizationRepository rsuOrganizationRepository;

    private final TestFixtures fixtures = new TestFixtures();

    @Test
    void postgisParsesClosedSampleRing() {
        Boolean parsed = jdbcTemplate.queryForObject(
                "SELECT ST_GeomFromText(?) IS NOT NULL", Boolean.class, CLOSED_SAMPLE_RING);

        assertEquals(Boolean.TRUE, parsed);
    }

    @Test
    void postgisRejectsUnclosedRing() {
        assertThrows(DataAccessException.class, () -> jdbcTemplate.queryForObject(
                "SELECT ST_GeomFromText(?) IS NOT NULL", Boolean.class, UNCLOSED_RING));
    }

    @Test
    void postgisRejectsThreePositionRing() {
        assertThrows(DataAccessException.class, () -> jdbcTemplate.queryForObject(
                "SELECT ST_GeomFromText(?) IS NOT NULL", Boolean.class, THREE_POSITION_RING));
    }

    @Test
    @Transactional
    void findRsuIps_matchesPostgisContainmentAndRejectsUnclosedRing() throws Exception {
        Organization organization = organizationRepository.save(fixtures.createOrg(null, ORGANIZATION, "geo@example.com"));
        Manufacturer manufacturer = fixtures.createRandomManufacturer();
        manufacturer.setName(MANUFACTURER);
        manufacturer = manufacturerRepository.save(manufacturer);
        RsuModel model = rsuModelRepository.save(fixtures.createRandomRsuModel(manufacturer));
        SnmpProtocol protocol = snmpProtocolRepository.save(fixtures.createRandomSnmpProtocol());
        SnmpCredential snmpCredential = snmpCredentialRepository.save(fixtures.createRandomSnmpCredential(organization));
        RsuCredential credential = rsuCredentialRepository.save(fixtures.createRandomRsuCredential(organization));

        Rsu rsu = fixtures.createRsu(RSU_IP, model, credential, snmpCredential, protocol);
        rsu.setGeography(fixtures.createPoint(-105.30, 39.70));
        rsu = rsuRepository.save(rsu);
        rsuOrganizationRepository.save(fixtures.createRsuOrganization(rsu, organization));
        entityManager.flush();

        assertEquals(List.of(RSU_IP), rsuGeoQueryService.findRsuIps(ORGANIZATION, sampleRing(), null));
        assertEquals(List.of(), rsuGeoQueryService.findRsuIps(ORGANIZATION, disjointRing(), null));
        assertEquals(List.of(RSU_IP), rsuGeoQueryService.findRsuIps(ORGANIZATION, sampleRing(), MANUFACTURER));
        assertEquals(List.of(), rsuGeoQueryService.findRsuIps(ORGANIZATION, sampleRing(), "Other Maker"));

        List<List<Double>> unclosed = new ArrayList<>();
        for (List<Double> point : sampleRing()) {
            unclosed.add(new ArrayList<>(point));
        }
        unclosed.removeLast();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> rsuGeoQueryService.findRsuIps(ORGANIZATION, unclosed, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Polygon ring must be closed", exception.getReason());
    }

    private static List<List<Double>> sampleRing() {
        return List.of(
                List.of(-105.34460908203145, 39.724583197251334),
                List.of(-105.34666901855489, 39.670180083300174),
                List.of(-105.25122529296911, 39.679162192647944),
                List.of(-105.2539718750002, 39.72088725644132),
                List.of(-105.34460908203145, 39.724583197251334));
    }

    private static List<List<Double>> disjointRing() {
        return List.of(
                List.of(0.0, 0.0),
                List.of(1.0, 0.0),
                List.of(1.0, 1.0),
                List.of(0.0, 1.0),
                List.of(0.0, 0.0));
    }
}
