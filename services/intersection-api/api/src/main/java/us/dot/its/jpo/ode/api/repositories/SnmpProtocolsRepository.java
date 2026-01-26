package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpProtocol;

import java.util.List;
import java.util.Optional;

@Repository
public interface SnmpProtocolsRepository extends JpaRepository<SnmpProtocol, Integer> {

    /**
     * Find SNMP protocol by nickname
     */
    Optional<SnmpProtocol> findByNickname(String nickname);

    /**
     * Get all SNMP protocol nicknames
     * Matches Python: get_allowed_selections() - snmp_version_nicknames_query
     */
    @Query("SELECT sp.nickname FROM SnmpProtocols sp ORDER BY sp.nickname ASC")
    List<String> findAllNicknames();
}
