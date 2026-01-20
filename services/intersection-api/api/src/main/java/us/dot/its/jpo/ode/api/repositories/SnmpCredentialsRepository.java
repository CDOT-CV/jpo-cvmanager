package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredentials;

import java.util.Optional;

@Repository
public interface SnmpCredentialsRepository extends JpaRepository<SnmpCredentials, Integer> {

    /**
     * Find SNMP credentials by nickname
     */
    Optional<SnmpCredentials> findByNickname(String nickname);
}
