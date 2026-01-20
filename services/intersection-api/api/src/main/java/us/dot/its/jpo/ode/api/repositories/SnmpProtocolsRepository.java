package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpProtocols;

import java.util.Optional;

@Repository
public interface SnmpProtocolsRepository extends JpaRepository<SnmpProtocols, Integer> {

    /**
     * Find SNMP protocol by nickname
     */
    Optional<SnmpProtocols> findByNickname(String nickname);
}
