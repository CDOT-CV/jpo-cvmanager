package us.dot.its.jpo.ode.api.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.fixtures.TestFixtures;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdConfig;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdType;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpProtocol;

@SpringBootTest
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
@Transactional
class SnmpMsgfwdConfigRepositoryTest {

    @Autowired
    private SnmpMsgfwdConfigRepository snmpMsgfwdConfigRepository;

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

    @Autowired
    private EntityManager entityManager;

    private final TestFixtures fixtures = new TestFixtures();

    @BeforeEach
    void setUp() {
        snmpMsgfwdConfigRepository.deleteAll();
        rsuOrganizationRepository.deleteAll();
        rsuRepository.deleteAll();
        rsuCredentialRepository.deleteAll();
        snmpCredentialRepository.deleteAll();
        snmpProtocolRepository.deleteAll();
        rsuModelRepository.deleteAll();
        manufacturerRepository.deleteAll();
        organizationRepository.deleteAll();
        entityManager.createQuery("DELETE FROM SnmpMsgfwdType").executeUpdate();
    }

    @Test
    @DisplayName("findByRsuIpv4AddressAndOrganizationName scopes by org, fetches type, and orders by type name then snmp_index")
    void findByRsuIpv4AddressAndOrganizationName_scopesAndOrders() throws Exception {
        Organization testOrg = organizationRepository.save(fixtures.createOrg(null, "TestOrg", "email"));
        Organization otherOrg = organizationRepository.save(fixtures.createOrg(null, "OtherOrg", "email"));
        RsuGraph graph = saveSharedGraph(testOrg);

        SnmpMsgfwdType dsrc = saveType("rsuDsrcFwd");
        SnmpMsgfwdType received = saveType("rsuReceivedMsg");

        Rsu rsu = saveRsu(graph, testOrg, "10.0.0.1");
        Rsu otherOrgRsu = saveRsu(graph, otherOrg, "10.0.0.2");

        saveConfig(rsu, received, 1, "bsm", true, true);
        saveConfig(rsu, dsrc, 2, "spat", true, false);
        saveConfig(rsu, dsrc, 1, "map", false, false);
        saveConfig(otherOrgRsu, dsrc, 1, "bsm", true, true);
        entityManager.flush();
        entityManager.clear();

        List<SnmpMsgfwdConfig> results = snmpMsgfwdConfigRepository
                .findByRsuIpv4AddressAndOrganizationName(InetAddress.getByName("10.0.0.1"), "TestOrg");

        assertEquals(List.of("rsuDsrcFwd:1", "rsuDsrcFwd:2", "rsuReceivedMsg:1"),
                results.stream().map(c -> c.getMsgfwdType().getName() + ":" + c.getId().getSnmpIndex()).toList());
        assertEquals("map", results.get(0).getMessageType());
        assertEquals(Boolean.FALSE, results.get(0).getActive());
        assertEquals(Boolean.TRUE, results.get(2).getSecurity());
        assertEquals("10.0.0.9", results.get(0).getDestIpv4().getHostAddress());
        assertEquals(Instant.parse("2024-04-01T06:00:00Z"), results.get(0).getStartDatetime());
    }

    @Test
    @DisplayName("findByRsuIpv4AddressAndOrganizationName returns nothing when the RSU is not in the organization")
    void findByRsuIpv4AddressAndOrganizationName_otherOrganization_returnsEmpty() throws Exception {
        Organization testOrg = organizationRepository.save(fixtures.createOrg(null, "TestOrg", "email"));
        organizationRepository.save(fixtures.createOrg(null, "OtherOrg", "email"));
        RsuGraph graph = saveSharedGraph(testOrg);

        SnmpMsgfwdType dsrc = saveType("rsuDsrcFwd");
        Rsu rsu = saveRsu(graph, testOrg, "10.0.0.1");
        saveConfig(rsu, dsrc, 1, "bsm", true, true);
        entityManager.flush();

        assertTrue(snmpMsgfwdConfigRepository
                .findByRsuIpv4AddressAndOrganizationName(InetAddress.getByName("10.0.0.1"), "OtherOrg").isEmpty());
        assertTrue(snmpMsgfwdConfigRepository
                .findByRsuIpv4AddressAndOrganizationName(InetAddress.getByName("10.0.0.3"), "TestOrg").isEmpty());
    }

    private SnmpMsgfwdType saveType(String name) {
        SnmpMsgfwdType type = new SnmpMsgfwdType();
        type.setName(name);
        entityManager.persist(type);
        return type;
    }

    /**
     * Inserted with native SQL because active/security are bit(1) columns, which the entity
     * cannot write from a Boolean without a column transformer.
     */
    private void saveConfig(Rsu rsu, SnmpMsgfwdType type, int snmpIndex, String messageType, boolean active,
            boolean security) {
        entityManager.flush();
        entityManager.createNativeQuery("INSERT INTO snmp_msgfwd_config "
                + "(rsu_id, msgfwd_type, snmp_index, message_type, dest_ipv4, dest_port, start_datetime, end_datetime, active, security) "
                + "VALUES (:rsuId, :typeId, :snmpIndex, :messageType, CAST(:destIp AS inet), 46800, :start, :end, "
                + "CAST(CAST(:active AS integer) AS bit(1)), CAST(CAST(:security AS integer) AS bit(1)))")
                .setParameter("rsuId", rsu.getId())
                .setParameter("typeId", type.getId())
                .setParameter("snmpIndex", snmpIndex)
                .setParameter("messageType", messageType)
                .setParameter("destIp", "10.0.0.9")
                .setParameter("start", Timestamp.from(Instant.parse("2024-04-01T06:00:00Z")))
                .setParameter("end", Timestamp.from(Instant.parse("2034-04-01T06:00:00Z")))
                .setParameter("active", active ? 1 : 0)
                .setParameter("security", security ? 1 : 0)
                .executeUpdate();
    }

    private RsuGraph saveSharedGraph(Organization owner) {
        Manufacturer manufacturer = manufacturerRepository.save(fixtures.createRandomManufacturer());
        RsuModel model = rsuModelRepository.save(fixtures.createRandomRsuModel(manufacturer));
        SnmpProtocol protocol = snmpProtocolRepository.save(fixtures.createRandomSnmpProtocol());
        SnmpCredential snmpCredential = snmpCredentialRepository.save(fixtures.createRandomSnmpCredential(owner));
        RsuCredential rsuCredential = rsuCredentialRepository.save(fixtures.createRandomRsuCredential(owner));
        return new RsuGraph(model, rsuCredential, snmpCredential, protocol);
    }

    private Rsu saveRsu(RsuGraph graph, Organization org, String ip) throws Exception {
        Rsu rsu = rsuRepository.save(fixtures.createRsu(ip, graph.model, graph.rsuCredential,
                graph.snmpCredential, graph.protocol));
        rsuOrganizationRepository.save(fixtures.createRsuOrganization(rsu, org));
        return rsu;
    }

    private record RsuGraph(RsuModel model, RsuCredential rsuCredential, SnmpCredential snmpCredential,
            SnmpProtocol protocol) {
    }
}
