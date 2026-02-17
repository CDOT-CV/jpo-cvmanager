package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;

import java.util.List;
import java.util.Optional;

@Repository
public interface SnmpCredentialRepository extends JpaRepository<SnmpCredential, Integer> {
    @Query("SELECT sc.nickname FROM SnmpCredential sc ORDER BY sc.nickname ASC")
    List<String> findAllNicknames();

    Optional<SnmpCredential> findByNickname(String nickname);

    List<SnmpCredential> findByOwnerOrganizationId(Integer ownerOrganizationId);

    @Query("select (count(s) > 0) from SnmpCredential s where s.nickname = ?1")
    boolean existsByNickname(String nickname);
}
