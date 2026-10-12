package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;

@Repository
public interface RsuCredentialRepository extends JpaRepository<RsuCredential, Integer> {
    @Query("SELECT rc.nickname FROM RsuCredential rc ORDER BY rc.nickname ASC")
    List<String> findAllNicknames();

    Optional<RsuCredential> findByNickname(String nickname);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndOwnerOrganizationIn(String nickname, List<Organization> organizations);

    @Modifying
    @Transactional
    void removeByOwnerOrganization(Organization organization);
}
