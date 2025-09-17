package us.dot.its.jpo.ode.api.services.firmware;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import us.dot.its.jpo.ode.api.models.firmware.FirmwareFile;
import us.dot.its.jpo.ode.api.models.firmware.FirmwareRule;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturers;
import us.dot.its.jpo.ode.api.accessors.firmware.FirmwareFileRepository;
import us.dot.its.jpo.ode.api.accessors.firmware.FirmwareRuleRepository;

import java.io.InputStream;
import java.util.List;

/**
 * Implementation of firmware service operations
 * Works with the actual database schema (firmware_images and
 * firmware_upgrade_rules tables)
 */
@Service
public class FirmwareServiceImpl implements FirmwareService {

    @Autowired
    private FirmwareFileRepository firmwareFileRepository;

    @Autowired
    private FirmwareRuleRepository firmwareRuleRepository;

    @Autowired
    private StorageServiceFactory storageServiceFactory;

    @Override
    @Transactional
    public FirmwareFile addFirmwareVersion(MultipartFile file, String deviceType, String description,
            List<FirmwareRule> rules, String createdBy) throws FirmwareServiceException {
        try {
            // Validate rules
            validateFirmwareRules(rules);

            // Check for duplicate file hash
            CloudStorageService storageService = storageServiceFactory.getStorageService();
            String fileHash = storageService.calculateFileHash(file);

            if (firmwareFileRepository.existsByFileHash(fileHash)) {
                throw new FirmwareServiceException(
                        "Firmware file with this hash already exists. Duplicate file detected.");
            }

            // Upload file to cloud storage
            String storagePath = storageService.uploadFirmwareFile(file, deviceType, "firmware");

            // Calculate checksum (for backward compatibility)
            String checksum = storageService.calculateChecksum(file);

            // Create manufacturer object (default to manufacturer ID 1)
            Manufacturers manufacturer = new Manufacturers();
            manufacturer.setManufacturerId(1);

            // Create firmware file entity
            FirmwareFile firmwareFile = new FirmwareFile(
                    file.getOriginalFilename(),
                    1, // Default model - should be determined based on device type
                    manufacturer, // Default manufacturer - should be determined based on device type
                    file.getOriginalFilename(),
                    extractVersionFromFilename(file.getOriginalFilename()),
                    description,
                    checksum,
                    storagePath,
                    createdBy,
                    FirmwareFile.DeviceType.valueOf(deviceType.toUpperCase()),
                    fileHash,
                    file.getSize());

            // Save firmware file
            firmwareFile = firmwareFileRepository.save(firmwareFile);

            // Create and save rules
            for (FirmwareRule rule : rules) {
                // Set the toFirmware to the current firmware file
                rule.setToFirmware(firmwareFile);
                firmwareRuleRepository.save(rule);
            }

            // Refresh the entity to include rules
            firmwareFile = firmwareFileRepository.findById(firmwareFile.getFirmwareId()).orElse(firmwareFile);

            return firmwareFile;

        } catch (Exception e) {
            throw new FirmwareServiceException("Failed to add firmware version: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public FirmwareFile modifyFirmwareVersion(Integer firmwareId, List<FirmwareRule> rules, String modifiedBy)
            throws FirmwareServiceException {
        try {
            // Validate rules
            validateFirmwareRules(rules);

            // Get existing firmware file
            FirmwareFile firmwareFile = firmwareFileRepository.findById(firmwareId)
                    .orElseThrow(() -> new FirmwareServiceException("Firmware file not found: " + firmwareId));

            // Delete existing rules
            firmwareRuleRepository.deleteByToFirmware(firmwareFile);

            // Create new rules
            for (FirmwareRule rule : rules) {
                rule.setToFirmware(firmwareFile);
                firmwareRuleRepository.save(rule);
            }

            // Refresh the entity to include new rules
            firmwareFile = firmwareFileRepository.findById(firmwareId).orElse(firmwareFile);

            return firmwareFile;

        } catch (Exception e) {
            throw new FirmwareServiceException("Failed to modify firmware version: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void removeFirmwareVersion(Integer firmwareId, String removedBy) throws FirmwareServiceException {
        try {
            // Get firmware file
            FirmwareFile firmwareFile = firmwareFileRepository.findById(firmwareId)
                    .orElseThrow(() -> new FirmwareServiceException("Firmware file not found: " + firmwareId));

            // Delete associated rules
            firmwareRuleRepository.deleteByToFirmware(firmwareFile);

            // Delete file from cloud storage
            CloudStorageService storageService = storageServiceFactory.getStorageService();
            storageService.deleteFirmwareFile(firmwareFile.getStoragePath());

            // Delete firmware file from database
            firmwareFileRepository.delete(firmwareFile);

        } catch (Exception e) {
            throw new FirmwareServiceException("Failed to remove firmware version: " + e.getMessage(), e);
        }
    }

    @Override
    public List<FirmwareFile> getFirmwareFiles(String deviceType) {
        FirmwareFile.DeviceType type = FirmwareFile.DeviceType.valueOf(deviceType.toUpperCase());
        return firmwareFileRepository.findByDeviceTypeAndIsActiveTrue(type);
    }

    @Override
    public FirmwareFile getFirmwareFile(Integer firmwareId) throws FirmwareServiceException {
        return firmwareFileRepository.findById(firmwareId)
                .orElseThrow(() -> new FirmwareServiceException("Firmware file not found: " + firmwareId));
    }

    @Override
    public InputStream downloadFirmwareFile(Integer firmwareId) throws FirmwareServiceException {
        try {
            FirmwareFile firmwareFile = getFirmwareFile(firmwareId);
            CloudStorageService storageService = storageServiceFactory.getStorageService();
            return storageService.downloadFirmwareFile(firmwareFile.getStoragePath());
        } catch (Exception e) {
            throw new FirmwareServiceException("Failed to download firmware file: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateFirmwareRules(List<FirmwareRule> rules) throws FirmwareServiceException {
        if (rules == null || rules.isEmpty()) {
            throw new FirmwareServiceException("At least one upgrade rule is required");
        }

        for (FirmwareRule rule : rules) {
            if (rule.getFromId() == null) {
                throw new FirmwareServiceException("Rule from ID is required");
            }

            if (rule.getToId() == null) {
                throw new FirmwareServiceException("Rule to ID is required");
            }
        }
    }

    /**
     * Check if firmware file with given hash exists (for duplicate detection)
     * 
     * @param fileHash SHA-256 file hash
     * @return true if firmware with hash exists, false otherwise
     */
    public boolean firmwareExistsByHash(String fileHash) {
        return firmwareFileRepository.existsByFileHash(fileHash);
    }

    /**
     * Find firmware files by file hash
     * 
     * @param fileHash SHA-256 file hash
     * @return List of firmware files with matching hash
     */
    public List<FirmwareFile> findFirmwareByHash(String fileHash) {
        return firmwareFileRepository.findByFileHash(fileHash);
    }

    /**
     * Find firmware files by file hash and device type
     * 
     * @param fileHash   SHA-256 file hash
     * @param deviceType RSU or OBU
     * @return List of firmware files with matching hash and device type
     */
    public List<FirmwareFile> findFirmwareByHashAndDeviceType(String fileHash, FirmwareFile.DeviceType deviceType) {
        return firmwareFileRepository.findByFileHashAndDeviceType(fileHash, deviceType);
    }

    private String extractVersionFromFilename(String filename) {
        // Simple version extraction - can be enhanced based on naming conventions
        if (filename == null)
            return "unknown";

        // Look for version pattern like v1.2.3 or 1.2.3
        String[] parts = filename.split("[._-]");
        for (String part : parts) {
            if (part.matches("v?\\d+\\.\\d+\\.\\d+")) {
                return part.startsWith("v") ? part.substring(1) : part;
            }
        }

        return "unknown";
    }
}
