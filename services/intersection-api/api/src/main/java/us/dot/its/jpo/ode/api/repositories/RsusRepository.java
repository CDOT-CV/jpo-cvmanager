package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsus;
import us.dot.its.jpo.ode.api.models.postgres.derived.RsuDetailedInfoRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RsusRepository extends JpaRepository<Rsus, UUID> {

    /**
     * Find RSU by IPv4 address
     */
    @Query(value = "SELECT * FROM rsus WHERE ipv4_address = CAST(:ipv4Address AS inet)", nativeQuery = true)
    Optional<Rsus> findByIpv4Address(@Param("ipv4Address") String ipv4Address);

    /**
     * Get distinct primary routes from all RSUs
     */
    @Query("SELECT DISTINCT r.primaryRoute FROM Rsus r ORDER BY r.primaryRoute ASC")
    List<String> findDistinctPrimaryRoutes();

    /**
     * Get detailed RSU information rows with all related data
     * Returns one row per RSU-organization relationship
     * These rows need to be aggregated by service layer to group organizations
     */
    @Query(value = "SELECT " +
            "r.ipv4_address::text as ipv4Address, " +
            "r.geography as geography, " +
            "r.milepost as milepost, " +
            "r.primary_route as primaryRoute, " +
            "r.serial_number as serialNumber, " +
            "r.iss_scms_id as issScmsId, " +
            "CONCAT(man.name, ' ', rm.name) as model, " +
            "rc.nickname as sshCredential, " +
            "sc.nickname as snmpCredential, " +
            "sp.nickname as snmpVersion, " +
            "o.name as orgName " +
            "FROM rsus r " +
            "JOIN rsu_models rm ON rm.rsu_model_id = r.model " +
            "JOIN manufacturers man ON man.manufacturer_id = rm.manufacturer " +
            "JOIN rsu_credentials rc ON rc.credential_id = r.credential_id " +
            "JOIN snmp_credentials sc ON sc.snmp_credential_id = r.snmp_credential_id " +
            "JOIN snmp_protocols sp ON sp.snmp_protocol_id = r.snmp_protocol_id " +
            "JOIN rsu_organization ro ON ro.rsu_id = r.rsu_id " +
            "JOIN organizations o ON o.organization_id = ro.organization_id " +
            "WHERE r.ipv4_address = CAST(:ipv4Address AS inet)", nativeQuery = true)
    List<RsuDetailedInfoRow> findDetailedRsuInfoRowsByIp(@Param("ipv4Address") String ipv4Address);

    /**
     * Get all detailed RSU information rows filtered by organization
     * Returns one row per RSU-organization relationship
     */
    @Query(value = "SELECT " +
            "r.ipv4_address::text as ipv4Address, " +
            "r.geography as geography, " +
            "r.milepost as milepost, " +
            "r.primary_route as primaryRoute, " +
            "r.serial_number as serialNumber, " +
            "r.iss_scms_id as issScmsId, " +
            "CONCAT(man.name, ' ', rm.name) as model, " +
            "rc.nickname as sshCredential, " +
            "sc.nickname as snmpCredential, " +
            "sp.nickname as snmpVersion, " +
            "o.name as orgName " +
            "FROM rsus r " +
            "JOIN rsu_models rm ON rm.rsu_model_id = r.model " +
            "JOIN manufacturers man ON man.manufacturer_id = rm.manufacturer " +
            "JOIN rsu_credentials rc ON rc.credential_id = r.credential_id " +
            "JOIN snmp_credentials sc ON sc.snmp_credential_id = r.snmp_credential_id " +
            "JOIN snmp_protocols sp ON sp.snmp_protocol_id = r.snmp_protocol_id " +
            "JOIN rsu_organization ro ON ro.rsu_id = r.rsu_id " +
            "JOIN organizations o ON o.organization_id = ro.organization_id " +
            "WHERE o.name = :organizationName", nativeQuery = true)
    List<RsuDetailedInfoRow> findAllDetailedRsuInfoRowsByOrganization(
            @Param("organizationName") String organizationName);

    /**
     * Get all detailed RSU information rows
     * Returns one row per RSU-organization relationship
     */
    @Query(value = "SELECT " +
            "r.ipv4_address::text as ipv4Address, " +
            "r.geography as geography, " +
            "r.milepost as milepost, " +
            "r.primary_route as primaryRoute, " +
            "r.serial_number as serialNumber, " +
            "r.iss_scms_id as issScmsId, " +
            "CONCAT(man.name, ' ', rm.name) as model, " +
            "rc.nickname as sshCredential, " +
            "sc.nickname as snmpCredential, " +
            "sp.nickname as snmpVersion, " +
            "o.name as orgName " +
            "FROM rsus r " +
            "JOIN rsu_models rm ON rm.rsu_model_id = r.model " +
            "JOIN manufacturers man ON man.manufacturer_id = rm.manufacturer " +
            "JOIN rsu_credentials rc ON rc.credential_id = r.credential_id " +
            "JOIN snmp_credentials sc ON sc.snmp_credential_id = r.snmp_credential_id " +
            "JOIN snmp_protocols sp ON sp.snmp_protocol_id = r.snmp_protocol_id " +
            "JOIN rsu_organization ro ON ro.rsu_id = r.rsu_id " +
            "JOIN organizations o ON o.organization_id = ro.organization_id", nativeQuery = true)
    List<RsuDetailedInfoRow> findAllDetailedRsuInfoRows();

    /**
     * Get all detailed RSU information rows filtered by multiple organizations
     * Returns one row per RSU-organization relationship
     */
    @Query(value = "SELECT " +
            "r.ipv4_address::text as ipv4Address, " +
            "r.geography as geography, " +
            "r.milepost as milepost, " +
            "r.primary_route as primaryRoute, " +
            "r.serial_number as serialNumber, " +
            "r.iss_scms_id as issScmsId, " +
            "CONCAT(man.name, ' ', rm.name) as model, " +
            "rc.nickname as sshCredential, " +
            "sc.nickname as snmpCredential, " +
            "sp.nickname as snmpVersion, " +
            "o.name as orgName " +
            "FROM rsus r " +
            "JOIN rsu_models rm ON rm.rsu_model_id = r.model " +
            "JOIN manufacturers man ON man.manufacturer_id = rm.manufacturer " +
            "JOIN rsu_credentials rc ON rc.credential_id = r.credential_id " +
            "JOIN snmp_credentials sc ON sc.snmp_credential_id = r.snmp_credential_id " +
            "JOIN snmp_protocols sp ON sp.snmp_protocol_id = r.snmp_protocol_id " +
            "JOIN rsu_organization ro ON ro.rsu_id = r.rsu_id " +
            "JOIN organizations o ON o.organization_id = ro.organization_id " +
            "WHERE o.name IN :organizationNames", nativeQuery = true)
    List<RsuDetailedInfoRow> findAllDetailedRsuInfoRowsByOrganizations(
            @Param("organizationNames") List<String> organizationNames);

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
