package us.dot.its.jpo.ode.api.services.firmware;

import org.springframework.web.multipart.MultipartFile;
import us.dot.its.jpo.ode.api.models.firmware.FirmwareFile;
import us.dot.its.jpo.ode.api.models.firmware.FirmwareRule;

import java.util.List;

/**
 * Shared service interface for firmware management operations
 * Used by both RSU and OBU firmware controllers
 */
public interface FirmwareService {

    /**
     * Add a new firmware version with at least one upgrade rule
     * 
     * @param file        The firmware file to upload
     * @param deviceType  RSU or OBU
     * @param description Optional description
     * @param rules       List of upgrade rules (must have at least one)
     * @param createdBy   User who created the firmware
     * @return Created firmware file with rules
     * @throws FirmwareServiceException if operation fails
     */
    FirmwareFile addFirmwareVersion(MultipartFile file, String deviceType, String description,
            List<FirmwareRule> rules, String createdBy) throws FirmwareServiceException;

    /**
     * Modify existing firmware version rules
     * 
     * @param firmwareId ID of the firmware file
     * @param rules      Updated list of rules
     * @param modifiedBy User who modified the rules
     * @return Updated firmware file
     * @throws FirmwareServiceException if operation fails
     */
    FirmwareFile modifyFirmwareVersion(Integer firmwareId, List<FirmwareRule> rules, String modifiedBy)
            throws FirmwareServiceException;

    /**
     * Remove firmware version and associated rules
     * 
     * @param firmwareId ID of the firmware file to remove
     * @param removedBy  User who removed the firmware
     * @throws FirmwareServiceException if operation fails
     */
    void removeFirmwareVersion(Integer firmwareId, String removedBy) throws FirmwareServiceException;

    /**
     * Get all firmware files for a device type
     * 
     * @param deviceType RSU or OBU
     * @return List of firmware files with their rules
     */
    List<FirmwareFile> getFirmwareFiles(String deviceType);

    /**
     * Get firmware file by ID
     * 
     * @param firmwareId ID of the firmware file
     * @return Firmware file with rules
     * @throws FirmwareServiceException if not found
     */
    FirmwareFile getFirmwareFile(Integer firmwareId) throws FirmwareServiceException;

    /**
     * Download firmware file
     * 
     * @param firmwareId ID of the firmware file
     * @return InputStream of the firmware file
     * @throws FirmwareServiceException if download fails
     */
    java.io.InputStream downloadFirmwareFile(Integer firmwareId) throws FirmwareServiceException;

    /**
     * Validate firmware rules
     * 
     * @param rules List of rules to validate
     * @throws FirmwareServiceException if validation fails
     */
    void validateFirmwareRules(List<FirmwareRule> rules) throws FirmwareServiceException;
}
