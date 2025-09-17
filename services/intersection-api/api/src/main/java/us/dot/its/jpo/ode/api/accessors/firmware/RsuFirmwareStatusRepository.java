package us.dot.its.jpo.ode.api.accessors.firmware;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RsuFirmwareStatusRepository extends JpaRepository<Rsus, UUID> {

    /**
     * Find RSUs by current firmware version
     * 
     * @param firmwareVersion Current firmware version ID
     * @return List of RSUs with the specified firmware version
     */
    @Query("SELECT r FROM Rsus r WHERE r.firmware_version = :firmwareVersion")
    List<Rsus> findByFirmware_version(@Param("firmwareVersion") Integer firmwareVersion);

    /**
     * Find RSUs by target firmware version
     * 
     * @param targetFirmwareVersion Target firmware version ID
     * @return List of RSUs with the specified target firmware version
     */
    @Query("SELECT r FROM Rsus r WHERE r.target_firmware_version = :targetFirmwareVersion")
    List<Rsus> findByTarget_firmware_version(@Param("targetFirmwareVersion") Integer targetFirmwareVersion);

    /**
     * Find RSUs that have a target firmware version (pending upgrades)
     * 
     * @return List of RSUs with pending firmware upgrades
     */
    @Query("SELECT r FROM Rsus r WHERE r.target_firmware_version IS NOT NULL")
    List<Rsus> findWithPendingUpgrades();

    /**
     * Find RSUs by model that have pending firmware upgrades
     * 
     * @param modelId RSU model ID
     * @return List of RSUs with pending upgrades for the specified model
     */
    @Query("SELECT r FROM Rsus r WHERE r.model = :modelId AND r.target_firmware_version IS NOT NULL")
    List<Rsus> findWithPendingUpgradesByModel(@Param("modelId") Integer modelId);

    /**
     * Find RSUs that need firmware upgrades (have target but different from
     * current)
     * 
     * @return List of RSUs that need firmware upgrades
     */
    @Query("SELECT r FROM Rsus r WHERE r.target_firmware_version IS NOT NULL AND r.target_firmware_version != r.firmware_version")
    List<Rsus> findNeedingUpgrades();

    /**
     * Find RSUs by IP address
     * 
     * @param ipv4Address RSU IP address
     * @return Optional RSU with the specified IP address
     */
    @Query("SELECT r FROM Rsus r WHERE r.ipv4_address = :ipv4Address")
    Optional<Rsus> findByIpv4_address(@Param("ipv4Address") String ipv4Address);

    /**
     * Find RSUs by serial number
     * 
     * @param serialNumber RSU serial number
     * @return Optional RSU with the specified serial number
     */
    @Query("SELECT r FROM Rsus r WHERE r.serial_number = :serialNumber")
    Optional<Rsus> findBySerial_number(@Param("serialNumber") String serialNumber);

    /**
     * Count RSUs by firmware version
     * 
     * @param firmwareVersion Firmware version ID
     * @return Count of RSUs with the specified firmware version
     */
    @Query("SELECT COUNT(r) FROM Rsus r WHERE r.firmware_version = :firmwareVersion")
    long countByFirmware_version(@Param("firmwareVersion") Integer firmwareVersion);

    /**
     * Count RSUs with pending upgrades
     * 
     * @return Count of RSUs with pending firmware upgrades
     */
    @Query("SELECT COUNT(r) FROM Rsus r WHERE r.target_firmware_version IS NOT NULL")
    long countWithPendingUpgrades();

    /**
     * Find RSUs by organization that have pending upgrades
     * 
     * @param organizationId Organization ID
     * @return List of RSUs with pending upgrades for the specified organization
     */
    @Query("SELECT r FROM Rsus r JOIN RsuOrganization ro ON r.rsu_id = ro.rsu_id WHERE ro.organization_id = :organizationId AND r.target_firmware_version IS NOT NULL")
    List<Rsus> findWithPendingUpgradesByOrganization(@Param("organizationId") Integer organizationId);
}
