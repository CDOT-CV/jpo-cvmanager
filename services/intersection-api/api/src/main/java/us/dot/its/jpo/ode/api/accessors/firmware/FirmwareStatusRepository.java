package us.dot.its.jpo.ode.api.accessors.firmware;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import us.dot.its.jpo.ode.api.models.firmware.FirmwareStatus;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FirmwareStatusRepository extends JpaRepository<FirmwareStatus, Integer> {

    /**
     * Find all firmware statuses by device type
     * 
     * @param deviceType RSU or OBU
     * @return List of firmware statuses
     */
    List<FirmwareStatus> findByDeviceType(FirmwareStatus.DeviceType deviceType);

    /**
     * Find firmware status by device IP/ID
     * 
     * @param deviceIp Device IP address or ID
     * @return Optional firmware status
     */
    List<FirmwareStatus> findByRsuId(Integer rsuId);

    List<FirmwareStatus> findByObuSn(String obuSn);

    /**
     * Find firmware statuses by upgrade status
     * 
     * @param upgradeStatus Current upgrade status
     * @return List of firmware statuses
     */
    List<FirmwareStatus> findByUpgradeStatus(FirmwareStatus.UpgradeStatus upgradeStatus);

    /**
     * Find firmware statuses by device type and upgrade status
     * 
     * @param deviceType    RSU or OBU
     * @param upgradeStatus Current upgrade status
     * @return List of firmware statuses
     */
    List<FirmwareStatus> findByDeviceTypeAndUpgradeStatus(
            FirmwareStatus.DeviceType deviceType,
            FirmwareStatus.UpgradeStatus upgradeStatus);

    /**
     * Find firmware statuses updated after a specific time
     * 
     * @param lastUpdated Time threshold
     * @return List of firmware statuses
     */
    List<FirmwareStatus> findByLastUpdatedAfter(LocalDateTime lastUpdated);

    /**
     * Find firmware statuses with errors
     * 
     * @return List of firmware statuses with error messages
     */
    @Query("SELECT s FROM FirmwareStatus s WHERE s.errorMessage IS NOT NULL AND s.errorMessage != ''")
    List<FirmwareStatus> findWithErrors();

    /**
     * Find firmware statuses by device type with errors
     * 
     * @param deviceType RSU or OBU
     * @return List of firmware statuses with errors
     */
    @Query("SELECT s FROM FirmwareStatus s WHERE s.deviceType = :deviceType AND s.errorMessage IS NOT NULL AND s.errorMessage != ''")
    List<FirmwareStatus> findByDeviceTypeWithErrors(@Param("deviceType") FirmwareStatus.DeviceType deviceType);

    /**
     * Find active upgrades (not idle or completed)
     * 
     * @return List of active firmware statuses
     */
    @Query("SELECT s FROM FirmwareStatus s WHERE s.upgradeStatus NOT IN ('IDLE', 'COMPLETED', 'FAILED', 'CANCELLED')")
    List<FirmwareStatus> findActiveUpgrades();

    /**
     * Find active upgrades by device type
     * 
     * @param deviceType RSU or OBU
     * @return List of active firmware statuses
     */
    @Query("SELECT s FROM FirmwareStatus s WHERE s.deviceType = :deviceType AND s.upgradeStatus NOT IN ('IDLE', 'COMPLETED', 'FAILED', 'CANCELLED')")
    List<FirmwareStatus> findActiveUpgradesByDeviceType(@Param("deviceType") FirmwareStatus.DeviceType deviceType);

    /**
     * Count firmware statuses by device type
     * 
     * @param deviceType RSU or OBU
     * @return Count of firmware statuses
     */
    long countByDeviceType(FirmwareStatus.DeviceType deviceType);

    /**
     * Count firmware statuses by upgrade status
     * 
     * @param upgradeStatus Current upgrade status
     * @return Count of firmware statuses
     */
    long countByUpgradeStatus(FirmwareStatus.UpgradeStatus upgradeStatus);

    /**
     * Find firmware statuses by current version
     * 
     * @param currentVersion Current firmware version
     * @return List of firmware statuses
     */
    List<FirmwareStatus> findByCurrentVersion(String currentVersion);

    /**
     * Find firmware statuses by target version
     * 
     * @param targetVersion Target firmware version
     * @return List of firmware statuses
     */
    List<FirmwareStatus> findByTargetVersion(String targetVersion);
}
