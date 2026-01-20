package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredentials;

import java.util.List;
import java.util.Optional;

@Repository
public interface SnmpCredentialsRepository extends JpaRepository<SnmpCredentials, Integer> {

    /**
     * Find SNMP credentials by nickname
     */
    Optional<SnmpCredentials> findByNickname(String nickname);

    /**
     * Get all SNMP credential nicknames
     * Matches Python: get_allowed_selections() - snmp_credential_nicknames_query
     */
    @Query("SELECT sc.nickname FROM SnmpCredentials sc ORDER BY sc.nickname ASC")
    List<String> findAllNicknames();
}
