package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.derived.RsuDetailedInfoRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RsusRepository extends JpaRepository<Rsu, UUID> {

    /**
     * Find RSU by IPv4 address
     */
    @Query(value = "SELECT * FROM rsus WHERE ipv4_address = :ipv4Address::inet", nativeQuery = true)
    Optional<Rsu> findByIpv4Address(@Param("ipv4Address") String ipv4Address);

    /**
     * Get distinct primary routes from all RSUs
     */
    @Query("SELECT DISTINCT r.primaryRoute FROM Rsu r ORDER BY r.primaryRoute ASC")
    List<String> findDistinctPrimaryRoutes();

    /**
     * Get detailed RSU information rows with all related data
     * Returns one row per RSU-organization relationship
     * These rows need to be aggregated by service layer to group organizations
     */
    @Query("SELECT new us.dot.its.jpo.ode.api.models.postgres.derived.RsuDetailedInfoRow(" +
            "r.ipv4AddressText, " +
            "r.geography, " +
            "r.milepost, " +
            "r.primaryRoute, " +
            "r.serialNumber, " +
            "r.issScmsId, " +
            "CONCAT(man.name, ' ', rm.name), " +
            "rc.nickname, " +
            "sc.nickname, " +
            "sp.nickname, " +
            "o.name) " +
            "FROM Rsu r " +
            "JOIN RsuModels rm ON rm.rsuModelId = r.model " +
            "JOIN Manufacturers man ON man.manufacturerId = rm.manufacturer " +
            "JOIN RsuCredentials rc ON rc.credentialId = r.credentialId " +
            "JOIN SnmpCredentials sc ON sc.snmpCredentialId = r.snmpCredentialId " +
            "JOIN SnmpProtocols sp ON sp.snmpProtocolId = r.snmpProtocolId " +
            "JOIN RsuOrganization ro ON ro.rsu_id = r.rsuId " +
            "JOIN Organizations o ON o.organization_id = ro.organization_id " +
            "WHERE r.ipv4AddressText = :ipv4Address")
    List<RsuDetailedInfoRow> findDetailedRsuInfoRowsByIp(@Param("ipv4Address") String ipv4Address);

    /**
     * Get all detailed RSU information rows filtered by organization
     * Returns one row per RSU-organization relationship
     */
    @Query("SELECT new us.dot.its.jpo.ode.api.models.postgres.derived.RsuDetailedInfoRow(" +
            "r.ipv4AddressText, " +
            "r.geography, " +
            "r.milepost, " +
            "r.primaryRoute, " +
            "r.serialNumber, " +
            "r.issScmsId, " +
            "CONCAT(man.name, ' ', rm.name), " +
            "rc.nickname, " +
            "sc.nickname, " +
            "sp.nickname, " +
            "o.name) " +
            "FROM Rsu r " +
            "JOIN RsuModels rm ON rm.rsuModelId = r.model " +
            "JOIN Manufacturers man ON man.manufacturerId = rm.manufacturer " +
            "JOIN RsuCredentials rc ON rc.credentialId = r.credentialId " +
            "JOIN SnmpCredentials sc ON sc.snmpCredentialId = r.snmpCredentialId " +
            "JOIN SnmpProtocols sp ON sp.snmpProtocolId = r.snmpProtocolId " +
            "JOIN RsuOrganization ro ON ro.rsu_id = r.rsuId " +
            "JOIN Organizations o ON o.organization_id = ro.organization_id " +
            "WHERE o.name = :organizationName")
    List<RsuDetailedInfoRow> findAllDetailedRsuInfoRowsByOrganization(
            @Param("organizationName") String organizationName);

    /**
     * Update RSU information
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE rsus SET " +
            "geography = ST_GeomFromText(CONCAT('POINT(', :longitude, ' ', :latitude, ')')), " +
            "milepost = :milepost, " +
            "ipv4_address = :newIpv4Address, " +
            "serial_number = :serialNumber, " +
            "primary_route = :primaryRoute, " +
            "model = (SELECT rsu_model_id FROM rsu_models WHERE name = :modelName), " +
            "credential_id = (SELECT credential_id FROM rsu_credentials WHERE nickname = :sshCredential), " +
            "snmp_credential_id = (SELECT snmp_credential_id FROM snmp_credentials WHERE nickname = :snmpCredential), "
            +
            "snmp_protocol_id = (SELECT snmp_protocol_id FROM snmp_protocols WHERE nickname = :snmpVersion), " +
            "iss_scms_id = :scmsId " +
            "WHERE ipv4_address = :originalIpv4Address", nativeQuery = true)
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
     * Delete RSU by IPv4 address
     */
    @Modifying
    @Transactional
    void deleteByIpv4Address(String ipv4Address);
}
