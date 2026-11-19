package us.dot.its.jpo.ode.api.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.fixtures.TestFixtures;
import us.dot.its.jpo.ode.api.models.postgres.projections.RsuOnlineStatusProjection;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Ping;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpProtocol;
import us.dot.its.jpo.ode.api.services.RsuManagementService;

@SpringBootTest
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
@Transactional
class RsuRepositoryTest {

    @Autowired
    private RsuRepository rsuRepository;

    @Autowired
    private PingRepository pingRepository;

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

    private final TestFixtures fixtures = new TestFixtures();

    @BeforeEach
    void setUp() {
        pingRepository.deleteAll();
        rsuOrganizationRepository.deleteAll();
        rsuRepository.deleteAll();
        rsuCredentialRepository.deleteAll();
        snmpCredentialRepository.deleteAll();
        snmpProtocolRepository.deleteAll();
        rsuModelRepository.deleteAll();
        manufacturerRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    @DisplayName("findOnlineStatusPingsByOrganization executes JPQL: window filter, left join, org scope, newest-first")
    void findOnlineStatusPingsByOrganization_filtersCutoffAndIncludesRsusWithoutRecentPings() throws Exception {
        Instant now = Instant.parse("2026-08-03T12:00:00Z");
        Instant cutoff = now.minus(20, ChronoUnit.MINUTES);
        Instant inWindowNewer = now.minus(2, ChronoUnit.MINUTES);
        Instant inWindowOlder = now.minus(5, ChronoUnit.MINUTES);
        Instant beforeCutoff = now.minus(30, ChronoUnit.MINUTES);

        Organization testOrg = organizationRepository.save(fixtures.createOrg(null, "TestOrg", "email"));
        Organization otherOrg = organizationRepository.save(fixtures.createOrg(null, "OtherOrg", "email"));
        RsuGraph graph = saveSharedGraph(testOrg);

        Rsu online = saveRsu(graph, testOrg, "10.0.0.1");
        Rsu unstable = saveRsu(graph, testOrg, "10.0.0.2");
        Rsu neverPinged = saveRsu(graph, testOrg, "10.0.0.3");
        Rsu staleOnly = saveRsu(graph, testOrg, "10.0.0.4");
        Rsu otherOrgRsu = saveRsu(graph, otherOrg, "10.0.0.5");

        savePing(online, inWindowNewer, true);
        savePing(online, beforeCutoff, true);
        savePing(unstable, inWindowNewer, false);
        savePing(unstable, inWindowOlder, true);
        savePing(staleOnly, beforeCutoff, true);
        savePing(otherOrgRsu, inWindowNewer, true);

        List<RsuOnlineStatusProjection> results = rsuRepository.findOnlineStatusPingsByOrganization("TestOrg", cutoff);

        assertEquals(5, results.size(),
                "One in-window ping for .1, two for .2, and one null row each for .3 and .4");
        assertEquals(List.of("10.0.0.1", "10.0.0.2", "10.0.0.2", "10.0.0.3", "10.0.0.4"),
                results.stream().map(RsuRepositoryTest::ip).toList());

        assertEquals(inWindowNewer, results.get(0).getTimestamp());
        assertEquals(Boolean.TRUE, results.get(0).getResult());

        assertEquals(inWindowNewer, results.get(1).getTimestamp());
        assertEquals(Boolean.FALSE, results.get(1).getResult());
        assertEquals(inWindowOlder, results.get(2).getTimestamp());
        assertEquals(Boolean.TRUE, results.get(2).getResult());

        assertEquals(neverPinged.getIpv4Address(), results.get(3).getIpv4Address());
        assertNull(results.get(3).getTimestamp());
        assertNull(results.get(3).getResult());
        assertEquals(staleOnly.getIpv4Address(), results.get(4).getIpv4Address());
        assertNull(results.get(4).getTimestamp());
        assertNull(results.get(4).getResult());
    }

    @Test
    @DisplayName("findLatestSuccessfulPingTimestamp returns the newest successful ping only")
    void findLatestSuccessfulPingTimestamp_filtersSuccessAndLimitsToOne() throws Exception {
        Instant newestFailure = Instant.parse("2026-08-03T12:00:00Z");
        Instant newestSuccess = newestFailure.minus(5, ChronoUnit.MINUTES);
        Instant olderSuccess = newestFailure.minus(10, ChronoUnit.MINUTES);

        Organization testOrg = organizationRepository.save(fixtures.createOrg(null, "TestOrg", "email"));
        Organization otherOrg = organizationRepository.save(fixtures.createOrg(null, "OtherOrg", "email"));
        RsuGraph graph = saveSharedGraph(testOrg);

        Rsu rsu = saveRsu(graph, testOrg, "10.0.0.1");
        saveRsu(graph, otherOrg, "10.0.0.2");
        savePing(rsu, olderSuccess, true);
        savePing(rsu, newestSuccess, true);
        savePing(rsu, newestFailure, false);

        assertEquals(Optional.of(newestSuccess), rsuRepository.findLatestSuccessfulPingTimestamp(
                RsuManagementService.toIpv4Address("10.0.0.1")));

        assertTrue(rsuRepository.findLatestSuccessfulPingTimestamp(
                RsuManagementService.toIpv4Address("10.0.0.2")).isEmpty());
    }

    @Test
    @DisplayName("credential listing queries return owned credentials plus those used by the organization's RSUs")
    void credentialListingQueries_scopeToOwnedOrUsedByOrganization() throws Exception {
        Organization ownerOrg = organizationRepository.save(fixtures.createOrg(null, "OwnerOrg", "email"));
        Organization viewerOrg = organizationRepository.save(fixtures.createOrg(null, "ViewerOrg", "email"));
        Organization unrelatedOrg = organizationRepository.save(fixtures.createOrg(null, "UnrelatedOrg", "email"));
        RsuGraph graph = saveSharedGraph(ownerOrg);
        RsuCredential viewerSpareSsh = rsuCredentialRepository.save(fixtures.createRandomRsuCredential(viewerOrg));
        SnmpCredential viewerSpareSnmp = snmpCredentialRepository
                .save(fixtures.createRandomSnmpCredential(viewerOrg));

        // 10.0.0.1 is shared with the viewer org; 10.0.0.2 is visible to the owner only
        Rsu shared = saveRsu(graph, ownerOrg, "10.0.0.1");
        rsuOrganizationRepository.save(fixtures.createRsuOrganization(shared, viewerOrg));
        saveRsu(graph, ownerOrg, "10.0.0.2");

        String sshNickname = graph.rsuCredential().getNickname();
        String snmpNickname = graph.snmpCredential().getNickname();

        // Owner: its own credentials, used by both RSUs
        assertEquals(List.of(sshNickname), rsuCredentialRepository.findOwnedOrUsedByOrganization(ownerOrg)
                .stream().map(RsuCredential::getNickname).toList());
        assertEquals(List.of(sshNickname + "@10.0.0.1", sshNickname + "@10.0.0.2"),
                rsuCredentialRepository.findRsuUsageInOrganization(ownerOrg).stream()
                        .map(u -> u.getNickname() + "@" + u.getIpv4Address().getHostAddress()).toList());
        assertEquals(List.of(snmpNickname), snmpCredentialRepository.findOwnedOrUsedByOrganization(ownerOrg)
                .stream().map(SnmpCredential::getNickname).toList());
        assertEquals(List.of(snmpNickname + "@10.0.0.1", snmpNickname + "@10.0.0.2"),
                snmpCredentialRepository.findRsuUsageInOrganization(ownerOrg).stream()
                        .map(u -> u.getNickname() + "@" + u.getIpv4Address().getHostAddress()).toList());

        // Viewer: the owner's credentials (via the shared RSU only) plus its own unused ones
        List<RsuCredential> viewerSsh = rsuCredentialRepository.findOwnedOrUsedByOrganization(viewerOrg);
        assertEquals(Set.of(sshNickname, viewerSpareSsh.getNickname()),
                viewerSsh.stream().map(RsuCredential::getNickname).collect(Collectors.toSet()));
        assertEquals(2, viewerSsh.size());
        assertEquals("OwnerOrg", viewerSsh.stream().filter(c -> c.getNickname().equals(sshNickname)).findFirst()
                .orElseThrow().getOwnerOrganization().getName());
        assertEquals(List.of(sshNickname + "@10.0.0.1"),
                rsuCredentialRepository.findRsuUsageInOrganization(viewerOrg).stream()
                        .map(u -> u.getNickname() + "@" + u.getIpv4Address().getHostAddress()).toList());
        List<SnmpCredential> viewerSnmp = snmpCredentialRepository.findOwnedOrUsedByOrganization(viewerOrg);
        assertEquals(Set.of(snmpNickname, viewerSpareSnmp.getNickname()),
                viewerSnmp.stream().map(SnmpCredential::getNickname).collect(Collectors.toSet()));
        assertEquals(2, viewerSnmp.size());
        assertEquals(List.of(snmpNickname + "@10.0.0.1"),
                snmpCredentialRepository.findRsuUsageInOrganization(viewerOrg).stream()
                        .map(u -> u.getNickname() + "@" + u.getIpv4Address().getHostAddress()).toList());

        // Unrelated: nothing
        assertTrue(rsuCredentialRepository.findOwnedOrUsedByOrganization(unrelatedOrg).isEmpty());
        assertTrue(rsuCredentialRepository.findRsuUsageInOrganization(unrelatedOrg).isEmpty());
        assertTrue(snmpCredentialRepository.findOwnedOrUsedByOrganization(unrelatedOrg).isEmpty());
        assertTrue(snmpCredentialRepository.findRsuUsageInOrganization(unrelatedOrg).isEmpty());
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

    private Ping savePing(Rsu rsu, Instant timestamp, boolean result) {
        Ping ping = new Ping();
        ping.setRsu(rsu);
        ping.setTimestamp(timestamp);
        ping.setResult(result);
        return pingRepository.save(ping);
    }

    private static String ip(RsuOnlineStatusProjection ping) {
        return ping.getIpv4Address().getHostAddress();
    }

    private record RsuGraph(RsuModel model, RsuCredential rsuCredential, SnmpCredential snmpCredential,
            SnmpProtocol protocol) {
    }
}
