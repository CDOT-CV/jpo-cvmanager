package us.dot.its.jpo.ode.api.services.firmware;

import com.google.cloud.storage.*;
import com.google.cloud.storage.Storage.BlobListOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Google Cloud Storage implementation of CloudStorageService
 * Stores firmware files in Google Cloud Storage buckets
 * 
 * This implementation uses the Google Cloud Storage Java client library
 * to provide full GCP storage functionality.
 */
@Service
public class GcpCloudStorageServiceImpl extends AbstractCloudStorageService {

    @Value("${firmware.storage.gcp.bucket-name:}")
    private String bucketName;

    @Value("${firmware.storage.gcp.project-id:}")
    private String projectId;

    @Value("${firmware.storage.gcp.max-file-size:100MB}")
    private DataSize maxFileSize;

    @Override
    public String uploadFirmwareFile(MultipartFile file, String deviceType, String subdirectory)
            throws CloudStorageException {
        try {
            // Validate configuration and file
            validateConfiguration();
            validateFile(file, deviceType, maxFileSize.toBytes());

            // Generate storage path using common methods
            String datePath = generateDatePath();
            String uniqueFilename = generateUniqueFilename(file.getOriginalFilename());
            String storagePath = buildStoragePath(deviceType, subdirectory, datePath, uniqueFilename);

            // Create GCP Storage client
            Storage storage = createStorageClient();

            // Create blob ID and info
            BlobId blobId = BlobId.of(bucketName, storagePath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.getContentType())
                    .setMetadata(java.util.Map.of(
                            "original-filename",
                            file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown",
                            "device-type", deviceType,
                            "upload-timestamp", String.valueOf(System.currentTimeMillis())))
                    .build();

            // Upload file to GCP Storage
            try (InputStream inputStream = file.getInputStream()) {
                storage.createFrom(blobInfo, inputStream);
            }

            return storagePath;

        } catch (Exception e) {
            throw new CloudStorageException("Failed to upload firmware file to GCP: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream downloadFirmwareFile(String storagePath) throws CloudStorageException {
        try {
            // Validate configuration
            validateConfiguration();

            // Get blob from GCP Storage
            Blob blob = getBlob(storagePath);

            if (blob == null || !blob.exists()) {
                throw new CloudStorageException("Firmware file not found: " + storagePath);
            }

            // Return input stream for the blob content
            return new ByteArrayInputStream(blob.getContent());

        } catch (Exception e) {
            throw new CloudStorageException("Failed to download firmware file from GCP: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFirmwareFile(String storagePath) throws CloudStorageException {
        try {
            // Validate configuration
            validateConfiguration();

            // Create GCP Storage client and delete blob
            Storage storage = createStorageClient();
            BlobId blobId = BlobId.of(bucketName, storagePath);
            boolean deleted = storage.delete(blobId);

            if (!deleted) {
                throw new CloudStorageException("Failed to delete firmware file: " + storagePath);
            }

        } catch (Exception e) {
            throw new CloudStorageException("Failed to delete firmware file from GCP: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean fileExists(String storagePath) {
        try {
            // Validate configuration
            validateConfiguration();

            // Check if blob exists in GCP Storage
            Blob blob = getBlob(storagePath);
            return blob != null && blob.exists();

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long getFileSize(String storagePath) throws CloudStorageException {
        try {
            // Validate configuration
            validateConfiguration();

            // Get blob size from GCP Storage
            Blob blob = getBlob(storagePath);

            if (blob == null || !blob.exists()) {
                throw new CloudStorageException("Firmware file not found: " + storagePath);
            }

            return blob.getSize();

        } catch (Exception e) {
            throw new CloudStorageException("Failed to get file size from GCP: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> listFirmwareFiles(String deviceType) throws CloudStorageException {
        try {
            // Validate configuration
            validateConfiguration();

            // Create GCP Storage client
            Storage storage = createStorageClient();

            // List files with device type prefix
            List<String> files = new ArrayList<>();
            String prefix = deviceType.toLowerCase() + "/";

            for (Blob blob : storage.list(bucketName, BlobListOption.prefix(prefix)).iterateAll()) {
                files.add(blob.getName());
            }

            return files;

        } catch (Exception e) {
            throw new CloudStorageException("Failed to list firmware files from GCP: " + e.getMessage(), e);
        }
    }

    @Override
    public String generatePresignedUrl(String storagePath, int expirationMinutes) throws CloudStorageException {
        try {
            // Validate configuration
            validateConfiguration();

            // Create GCP Storage client
            Storage storage = createStorageClient();

            // Generate presigned URL for the blob
            BlobId blobId = BlobId.of(bucketName, storagePath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

            URL url = storage.signUrl(blobInfo, expirationMinutes, TimeUnit.MINUTES);
            return url.toString();

        } catch (Exception e) {
            throw new CloudStorageException("Failed to generate presigned URL from GCP: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "Google Cloud Storage";
    }

    @Override
    public String getStorageType() {
        return "GCP";
    }

    @Override
    public void validateFile(MultipartFile file, String deviceType) throws CloudStorageException {
        // Use common validation with GCP max file size
        validateFile(file, deviceType, maxFileSize.toBytes());
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

    /**
     * Create a GCP Storage client instance
     * 
     * @return Storage client
     * @throws CloudStorageException if client creation fails
     */
    private Storage createStorageClient() throws CloudStorageException {
        try {
            return StorageOptions.newBuilder()
                    .setProjectId(projectId)
                    .build()
                    .getService();
        } catch (Exception e) {
            throw new CloudStorageException("Failed to create GCP Storage client: " + e.getMessage(), e);
        }
    }

    /**
     * Get blob from GCP Storage
     * 
     * @param storagePath The storage path of the blob
     * @return Blob instance
     * @throws CloudStorageException if blob retrieval fails
     */
    private Blob getBlob(String storagePath) throws CloudStorageException {
        try {
            Storage storage = createStorageClient();
            BlobId blobId = BlobId.of(bucketName, storagePath);
            return storage.get(blobId);
        } catch (Exception e) {
            throw new CloudStorageException("Failed to get blob from GCP Storage: " + e.getMessage(), e);
        }
    }

    /**
     * Validate that GCP storage is properly configured
     * 
     * @throws CloudStorageException if not configured
     */
    private void validateConfiguration() throws CloudStorageException {
        if (!isConfigured()) {
            throw new CloudStorageException(
                    "GCP Storage not properly configured. Please check bucket-name and project-id settings.");
        }
    }
}