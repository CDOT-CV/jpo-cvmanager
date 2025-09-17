package us.dot.its.jpo.ode.api.services.firmware;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.List;

/**
 * Google Cloud Storage implementation of CloudStorageService
 * Delegates to the full implementation when GCP dependencies are available
 * 
 * This service acts as a facade that can fall back to local storage
 * when GCP is not properly configured or dependencies are missing.
 */
@Service
public class GcpCloudStorageService extends AbstractCloudStorageService {

    @Autowired(required = false)
    private GcpCloudStorageServiceImpl gcpImplementation;

    @Autowired
    private LocalFileStorageService localStorageService;

    @Value("${firmware.storage.gcp.bucket-name:}")
    private String bucketName;

    @Value("${firmware.storage.gcp.project-id:}")
    private String projectId;

    @Value("${firmware.storage.gcp.max-file-size:100MB}")
    private DataSize maxFileSize;

    @Override
    public String uploadFirmwareFile(MultipartFile file, String deviceType, String subdirectory)
            throws CloudStorageException {
        if (isGcpAvailable()) {
            return gcpImplementation.uploadFirmwareFile(file, deviceType, subdirectory);
        } else {
            // Fall back to local storage
            return localStorageService.uploadFirmwareFile(file, deviceType, subdirectory);
        }
    }

    @Override
    public InputStream downloadFirmwareFile(String storagePath) throws CloudStorageException {
        if (isGcpAvailable()) {
            return gcpImplementation.downloadFirmwareFile(storagePath);
        } else {
            // Fall back to local storage
            return localStorageService.downloadFirmwareFile(storagePath);
        }
    }

    @Override
    public void deleteFirmwareFile(String storagePath) throws CloudStorageException {
        if (isGcpAvailable()) {
            gcpImplementation.deleteFirmwareFile(storagePath);
        } else {
            // Fall back to local storage
            localStorageService.deleteFirmwareFile(storagePath);
        }
    }

    @Override
    public boolean fileExists(String storagePath) {
        if (isGcpAvailable()) {
            return gcpImplementation.fileExists(storagePath);
        } else {
            // Fall back to local storage
            return localStorageService.fileExists(storagePath);
        }
    }

    @Override
    public long getFileSize(String storagePath) throws CloudStorageException {
        if (isGcpAvailable()) {
            return gcpImplementation.getFileSize(storagePath);
        } else {
            // Fall back to local storage
            return localStorageService.getFileSize(storagePath);
        }
    }

    @Override
    public String generatePresignedUrl(String storagePath, int expirationMinutes) throws CloudStorageException {
        if (isGcpAvailable()) {
            return gcpImplementation.generatePresignedUrl(storagePath, expirationMinutes);
        } else {
            // Fall back to local storage
            return localStorageService.generatePresignedUrl(storagePath, expirationMinutes);
        }
    }

    @Override
    public List<String> listFirmwareFiles(String deviceType) throws CloudStorageException {
        if (isGcpAvailable()) {
            return gcpImplementation.listFirmwareFiles(deviceType);
        } else {
            // Fall back to local storage
            return localStorageService.listFirmwareFiles(deviceType);
        }
    }

    @Override
    public String getProviderName() {
        if (isGcpAvailable()) {
            return gcpImplementation.getProviderName();
        } else {
            return "Google Cloud Storage (Fallback to Local)";
        }
    }

    @Override
    public void validateFile(MultipartFile file, String deviceType) throws CloudStorageException {
        // Use common validation with GCP max file size
        validateFile(file, deviceType, maxFileSize.toBytes());
    }

    @Override
    public String getStorageType() {
        return "GCP";
    }

    /**
     * Check if GCP storage is properly configured and available
     * 
     * @return true if GCP is available, false otherwise
     */
    private boolean isGcpAvailable() {
        return gcpImplementation != null && gcpImplementation.isConfigured();
    }

    /**
     * Check if GCP storage is properly configured
     * 
     * @return true if configured, false otherwise
     */
    public boolean isConfigured() {
        return bucketName != null && !bucketName.trim().isEmpty() &&
                projectId != null && !projectId.trim().isEmpty();
    }
}