package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpProtocol;

import java.util.List;
import java.util.Optional;

@Repository
@RepositoryRestResource(exported = false)
public interface SnmpProtocolRepository extends JpaRepository<SnmpProtocol, Integer> {
    @Query("SELECT sp.nickname FROM SnmpProtocol sp ORDER BY sp.nickname ASC")
    List<String> findAllNicknames();

    Optional<SnmpProtocol> findByNickname(String nickname);
}
