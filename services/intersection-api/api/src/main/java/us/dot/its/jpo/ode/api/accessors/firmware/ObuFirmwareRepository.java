package us.dot.its.jpo.ode.api.accessors.firmware;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import us.dot.its.jpo.ode.api.models.firmware.FirmwareFile;
import us.dot.its.jpo.ode.api.models.firmware.FirmwareRule;
import us.dot.its.jpo.ode.api.services.firmware.FirmwareService;
import us.dot.its.jpo.ode.api.services.firmware.FirmwareServiceException;

import java.io.InputStream;
import java.util.List;

/**
 * Repository for OBU firmware operations
 * Delegates to shared firmware service with OBU-specific device type
 */
@Component
public class ObuFirmwareRepository {

    @Autowired
    private FirmwareService firmwareService;

    private static final String DEVICE_TYPE = "OBU";

    /**
     * Add OBU firmware version with upgrade rules
     * 
     * @param file        Firmware file to upload
     * @param description Optional description
     * @param rules       List of upgrade rules
     * @param createdBy   User who created the firmware
     * @return Created firmware file with rules
     * @throws FirmwareServiceException if operation fails
     */
    public FirmwareFile addFirmwareVersion(MultipartFile file, String description,
            List<FirmwareRule> rules, String createdBy) throws FirmwareServiceException {
        return firmwareService.addFirmwareVersion(file, DEVICE_TYPE, description, rules, createdBy);
    }

    /**
     * Modify OBU firmware version rules
     * 
     * @param firmwareId ID of the firmware file
     * @param rules      Updated list of rules
     * @param modifiedBy User who modified the rules
     * @return Updated firmware file
     * @throws FirmwareServiceException if operation fails
     */
    public FirmwareFile modifyFirmwareVersion(Integer firmwareId, List<FirmwareRule> rules, String modifiedBy)
            throws FirmwareServiceException {
        return firmwareService.modifyFirmwareVersion(firmwareId, rules, modifiedBy);
    }

    /**
     * Remove OBU firmware version
     * 
     * @param firmwareId ID of the firmware file to remove
     * @param removedBy  User who removed the firmware
     * @throws FirmwareServiceException if operation fails
     */
    public void removeFirmwareVersion(Integer firmwareId, String removedBy) throws FirmwareServiceException {
        firmwareService.removeFirmwareVersion(firmwareId, removedBy);
    }

    /**
     * Get all OBU firmware files
     * 
     * @return List of OBU firmware files with rules
     */
    public List<FirmwareFile> getFirmwareFiles() {
        return firmwareService.getFirmwareFiles(DEVICE_TYPE);
    }

    /**
     * Get OBU firmware file by ID
     * 
     * @param firmwareId ID of the firmware file
     * @return Firmware file with rules
     * @throws FirmwareServiceException if not found
     */
    public FirmwareFile getFirmwareFile(Integer firmwareId) throws FirmwareServiceException {
        return firmwareService.getFirmwareFile(firmwareId);
    }

    /**
     * Download OBU firmware file
     * 
     * @param firmwareId ID of the firmware file
     * @return InputStream of the firmware file
     * @throws FirmwareServiceException if download fails
     */
    public InputStream downloadFirmwareFile(Integer firmwareId) throws FirmwareServiceException {
        return firmwareService.downloadFirmwareFile(firmwareId);
    }
}
