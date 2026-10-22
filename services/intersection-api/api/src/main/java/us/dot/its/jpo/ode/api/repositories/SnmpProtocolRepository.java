package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpProtocol;

import java.util.List;
import java.util.Optional;

@Repository
public interface SnmpProtocolRepository extends JpaRepository<SnmpProtocol, Integer> {
    @Query("SELECT sp.nickname FROM SnmpProtocol sp ORDER BY sp.nickname ASC")
    List<String> findAllNicknames();

    @Query("SELECT sp.nickname FROM SnmpProtocol sp WHERE sp.protocolCode = '1218' ORDER BY sp.nickname ASC")
    List<String> findSupportedNicknames();

    Optional<SnmpProtocol> findByNickname(String nickname);
}
