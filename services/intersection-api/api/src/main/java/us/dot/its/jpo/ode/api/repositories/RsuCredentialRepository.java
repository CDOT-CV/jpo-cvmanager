package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    /**
     * Returns the credentials the organization owns, plus the credentials used by
     * any RSU associated with the organization (which it may not own).
     */
    @Query("SELECT rc FROM RsuCredential rc " +
            "JOIN FETCH rc.ownerOrganization " +
            "WHERE rc.ownerOrganization = :organization " +
            "OR EXISTS (SELECT 1 FROM Rsu r JOIN r.rsuOrganizations ro " +
            "WHERE r.credential = rc AND ro.organization = :organization) " +
            "ORDER BY rc.nickname ASC")
    List<RsuCredential> findOwnedOrUsedByOrganization(@Param("organization") Organization organization);

    /**
     * Returns one row per RSU associated with the organization, pairing the RSU's
     * IP with the nickname of its SSH credential.
     */
    @Query("SELECT rc.nickname AS nickname, r.ipv4Address AS ipv4Address " +
            "FROM Rsu r JOIN r.credential rc JOIN r.rsuOrganizations ro " +
            "WHERE ro.organization = :organization " +
            "ORDER BY r.ipv4Address ASC")
    List<CredentialRsuUsage> findRsuUsageInOrganization(@Param("organization") Organization organization);

    interface CredentialRsuUsage {
        String getNickname();

        InetAddress getIpv4Address();
    }
}
