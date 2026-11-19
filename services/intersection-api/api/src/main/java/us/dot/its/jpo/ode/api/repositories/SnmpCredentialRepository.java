package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.net.InetAddress;

import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;

import java.util.List;
import java.util.Optional;

@Repository
public interface SnmpCredentialRepository extends JpaRepository<SnmpCredential, Integer> {
    @Query("SELECT sc.nickname FROM SnmpCredential sc ORDER BY sc.nickname ASC")
    List<String> findAllNicknames();

    Optional<SnmpCredential> findByNickname(String nickname);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndOwnerOrganizationIn(String nickname, List<Organization> organizations);

    /**
     * Returns the credentials the organization owns, plus the credentials used by
     * any RSU associated with the organization (which it may not own).
     */
    @Query("SELECT sc FROM SnmpCredential sc " +
            "JOIN FETCH sc.ownerOrganization " +
            "WHERE sc.ownerOrganization = :organization " +
            "OR EXISTS (SELECT 1 FROM Rsu r JOIN r.rsuOrganizations ro " +
            "WHERE r.snmpCredential = sc AND ro.organization = :organization) " +
            "ORDER BY sc.nickname ASC")
    List<SnmpCredential> findOwnedOrUsedByOrganization(@Param("organization") Organization organization);

    /**
     * Returns one row per RSU associated with the organization, pairing the RSU's
     * IP with the nickname of its SNMP credential.
     */
    @Query("SELECT sc.nickname AS nickname, r.ipv4Address AS ipv4Address " +
            "FROM Rsu r JOIN r.snmpCredential sc JOIN r.rsuOrganizations ro " +
            "WHERE ro.organization = :organization " +
            "ORDER BY r.ipv4Address ASC")
    List<CredentialRsuUsage> findRsuUsageInOrganization(@Param("organization") Organization organization);

    interface CredentialRsuUsage {
        String getNickname();

        InetAddress getIpv4Address();
    }
}
