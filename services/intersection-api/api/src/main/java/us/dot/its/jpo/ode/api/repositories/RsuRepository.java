package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;

import java.util.List;
import java.util.Optional;

@Repository
public interface RsuRepository extends JpaRepository<Rsu, Integer> {

    /**
     * Find RSU by IPv4 address - Spring Data JPA auto-generated
     */
    Optional<Rsu> findByIpv4Address(String ipv4Address);

    /**
     * Get distinct primary routes from all RSUs
     */
    @Query("SELECT DISTINCT r.primaryRoute FROM Rsu r ORDER BY r.primaryRoute ASC")
    List<String> findDistinctPrimaryRoutes();

    /**
     * Update RSU information
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE rsus SET " +
            "geography = ST_GeomFromText(CONCAT('POINT(', :longitude, ' ', :latitude, ')')), " +
            "milepost = :milepost, " +
            "ipv4_address = :newIpv4Address::inet, " +
            "serial_number = :serialNumber, " +
            "primary_route = :primaryRoute, " +
            "model = (SELECT rsu_model_id FROM rsu_models WHERE name = :modelName), " +
            "credential_id = (SELECT credential_id FROM rsu_credentials WHERE nickname = :sshCredential), " +
            "snmp_credential_id = (SELECT snmp_credential_id FROM snmp_credentials WHERE nickname = :snmpCredential), " +
            "snmp_protocol_id = (SELECT snmp_protocol_id FROM snmp_protocols WHERE nickname = :snmpVersion), " +
            "iss_scms_id = :scmsId " +
            "WHERE ipv4_address = :originalIpv4Address::inet", nativeQuery = true)
    int updateRsuByIpv4Address(
            @Param("originalIpv4Address") String originalIpv4Address,
            @Param("newIpv4Address") String newIpv4Address,
            @Param("longitude") Double longitude,
            @Param("latitude") Double latitude,
            @Param("milepost") Double milepost,
            @Param("serialNumber") String serialNumber,
            @Param("primaryRoute") String primaryRoute,
            @Param("modelName") String modelName,
            @Param("sshCredential") String sshCredential,
            @Param("snmpCredential") String snmpCredential,
            @Param("snmpVersion") String snmpVersion,
            @Param("scmsId") String scmsId);

    /**
     * Delete RSU by IPv4 address - Spring Data JPA auto-generated
     */
    @Modifying
    @Transactional
    void deleteByIpv4Address(String ipv4Address);

    /**
     * Find allowed RSU IPs by user email using entity relationships
     */
    @Query("SELECT r.ipv4Address " +
           "FROM Rsu r " +
           "JOIN r.rsuOrganizations ro " +
           "JOIN ro.organization.userOrganizations uo " +
           "JOIN uo.user u " +
           "WHERE u.email = :email")
    List<String> findAllowedRsuIpByEmail(@Param("email") String email);

    /**
     * Check if RSU exists in any of the given organizations using entity relationships
     */
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END " +
           "FROM Rsu r " +
           "JOIN r.rsuOrganizations ro " +
           "JOIN ro.organization o " +
           "WHERE r.ipv4Address = :rsuIp AND o.name IN :organizations")
    boolean existsByIpAndOrganizations(@Param("rsuIp") String rsuIp, @Param("organizations") List<String> organizations);
}
