package us.dot.its.jpo.ode.api.services;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.fixtures.TestFixtures;
import us.dot.its.jpo.ode.api.models.postgres.tables.*;
import us.dot.its.jpo.ode.api.repositories.SnmpMsgfwdConfigRepository;
import us.dot.its.jpo.snmp.client.*;
import us.dot.its.jpo.snmp.rsu.ntcip1218.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
class RsuMessageForwardingPersistenceTest {
    @Autowired RsuMessageForwardingSnapshotService snapshots;
    @Autowired RsuMessageForwardingReadService reads;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean SnmpMsgfwdConfigRepository configs;

    @Test void replacementIsScopedAndFailureAfterDeleteRollsBack() {
        var tx = new TransactionTemplate(transactionManager);
        var fixtures = new TestFixtures();
        var rsus = tx.execute(status -> {
            var org = fixtures.createRandomOrg(); em.persist(org);
            var manufacturer = fixtures.createRandomManufacturer(); em.persist(manufacturer);
            var model = fixtures.createRandomRsuModel(manufacturer); em.persist(model);
            var ssh = fixtures.createRandomRsuCredential(org); em.persist(ssh);
            var credential = fixtures.createRandomSnmpCredential(org); em.persist(credential);
            var protocol = fixtures.createRandomSnmpProtocol(); em.persist(protocol);
            for (var name : List.of("rsuReceivedMsg", "rsuXmitMsgFwding")) {
                if (em.createQuery("select t from SnmpMsgfwdType t where t.name = :name", SnmpMsgfwdType.class)
                        .setParameter("name", name).getResultList().isEmpty()) {
                    var type = new SnmpMsgfwdType(); type.setName(name); em.persist(type);
                }
            }
            try {
                var first = fixtures.createRsu("192.0.2.210", model, ssh, credential, protocol); em.persist(first);
                var second = fixtures.createRsu("192.0.2.211", model, ssh, credential, protocol); em.persist(second);
                em.flush();
                return List.of(first, second);
            } catch (java.net.UnknownHostException e) { throw new IllegalStateException(e); }
        });
        var bindings = new Ntcip1218Client(mock(SnmpClient.class), Clock.systemUTC())
                .createBindings(new ForwardingRequest(MessageType.MAP, "198.51.100.3", true), 1);
        var columns = bindings.stream().collect(Collectors.toMap(b -> {
            var parts = b.oid().value().split("\\."); return Integer.parseInt(parts[parts.length - 2]);
        }, SnmpBinding::value));
        columns.put(9, new SnmpValue.IntegerValue(1));
        var row = new ForwardingRow(ForwardingTable.TRANSMIT, 1, columns);
        try {
            for (var rsu : rsus) snapshots.replace(rsu.getId(), rsu.getIpv4Address().getHostAddress(), List.of(row));
            var cached = reads.cached("192.0.2.210");
            assertEquals(1, cached.size());
            assertEquals("MAP", cached.getFirst().messageType());
            assertEquals(44920, cached.getFirst().destinationPort());
            assertTrue(cached.getFirst().active());
            assertTrue(cached.getFirst().includeSecurity());
            var ids = rsus.stream().map(Rsu::getId).toList();
            assertEquals(2, configs.findAll().stream().filter(c -> ids.contains(c.getId().getRsuId())).count());
            snapshots.replace(rsus.getFirst().getId(), "192.0.2.210", List.of());
            var remaining = configs.findAll().stream().filter(c -> ids.contains(c.getId().getRsuId())).toList();
            assertEquals(1, remaining.size());
            assertEquals(rsus.getLast().getId(), remaining.getFirst().getId().getRsuId());
            doThrow(new DataIntegrityViolationException("fixture persistence failure")).when(configs).saveAllAndFlush(anyList());
            assertThrows(DataIntegrityViolationException.class,
                    () -> snapshots.replace(rsus.getLast().getId(), "192.0.2.211", List.of()));
            remaining = configs.findAll().stream().filter(c -> ids.contains(c.getId().getRsuId())).toList();
            assertEquals(1, remaining.size(), "The delete must roll back with the failed replacement");
            assertEquals(rsus.getLast().getId(), remaining.getFirst().getId().getRsuId());
        } finally {
            reset(configs);
            tx.executeWithoutResult(status -> {
                em.createQuery("delete from SnmpMsgfwdConfig c where c.id.rsuId in :ids")
                        .setParameter("ids", rsus.stream().map(Rsu::getId).toList()).executeUpdate();
                for (var rsu : rsus) em.remove(em.getReference(Rsu.class, rsu.getId()));
                em.flush();
                var first = rsus.getFirst();
                em.remove(em.getReference(RsuCredential.class, first.getCredential().getId()));
                em.remove(em.getReference(SnmpCredential.class, first.getSnmpCredential().getId()));
                em.remove(em.getReference(SnmpProtocol.class, first.getSnmpProtocol().getId()));
                em.remove(em.getReference(RsuModel.class, first.getModel().getId()));
                em.flush();
                em.remove(em.getReference(Manufacturer.class, first.getModel().getManufacturer().getId()));
                em.remove(em.getReference(Organization.class, first.getCredential().getOwnerOrganization().getId()));
            });
        }
    }
}
