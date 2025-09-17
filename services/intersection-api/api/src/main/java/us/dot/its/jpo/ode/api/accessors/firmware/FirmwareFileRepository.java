package us.dot.its.jpo.ode.api.accessors.firmware;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import us.dot.its.jpo.ode.api.models.firmware.FirmwareFile;

import java.util.List;

@Repository
public interface FirmwareFileRepository extends JpaRepository<FirmwareFile, Integer> {

    /**
     * Find all firmware files by device type and active status
     * 
     * @param deviceType RSU or OBU
     * @param isActive   Active status
     * @return List of firmware files
     */
    List<FirmwareFile> findByDeviceTypeAndIsActiveTrue(FirmwareFile.DeviceType deviceType);

    /**
     * Find firmware file by filename and device type
     * 
     * @param filename   Filename to search for
     * @param deviceType RSU or OBU
     * @return Optional firmware file
     */
    List<FirmwareFile> findByNameAndDeviceType(String name, FirmwareFile.DeviceType deviceType);

    /**
     * Find firmware file by version and device type
     * 
     * @param version    Version to search for
     * @param deviceType RSU or OBU
     * @return List of firmware files with matching version
     */
    List<FirmwareFile> findByVersionAndDeviceType(String version, FirmwareFile.DeviceType deviceType);

    /**
     * Find firmware files by checksum
     * 
     * @param checksum SHA-256 checksum
     * @return List of firmware files with matching checksum
     */
    List<FirmwareFile> findByChecksum(String checksum);

    /**
     * Find firmware files by file hash
     * 
     * @param fileHash SHA-256 file hash
     * @return List of firmware files with matching file hash
     */
    List<FirmwareFile> findByFileHash(String fileHash);

    /**
     * Find firmware files by file hash and device type
     * 
     * @param fileHash   SHA-256 file hash
     * @param deviceType RSU or OBU
     * @return List of firmware files with matching hash and device type
     */
    List<FirmwareFile> findByFileHashAndDeviceType(String fileHash, FirmwareFile.DeviceType deviceType);

    /**
     * Check if firmware file with given hash exists
     * 
     * @param fileHash SHA-256 file hash
     * @return true if firmware with hash exists, false otherwise
     */
    boolean existsByFileHash(String fileHash);

    /**
     * Find firmware files created by a specific user
     * 
     * @param createdBy Username of creator
     * @return List of firmware files
     */
    List<FirmwareFile> findByCreatedBy(String createdBy);

    /**
     * Count firmware files by device type
     * 
     * @param deviceType RSU or OBU
     * @return Count of firmware files
     */
    long countByDeviceType(FirmwareFile.DeviceType deviceType);

    /**
     * Find firmware files with their associated rules
     * 
     * @param deviceType RSU or OBU
     * @return List of firmware files with rules
     */
    @Query("SELECT f FROM FirmwareFile f LEFT JOIN FETCH f.rules WHERE f.deviceType = :deviceType AND f.isActive = true")
    List<FirmwareFile> findByDeviceTypeWithRules(@Param("deviceType") FirmwareFile.DeviceType deviceType);
}
