package us.dot.its.jpo.ode.api.services.firmware;

import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.List;

/**
 * Abstract interface for cloud storage operations
 * Supports local file system, GCP, AWS S3, and other cloud providers
 */
public interface CloudStorageService {

    /**
     * Upload a firmware file to cloud storage
     * 
     * @param file         The multipart file to upload
     * @param deviceType   RSU or OBU
     * @param subdirectory Subdirectory for organization
     * @return Storage path where the file was saved
     * @throws CloudStorageException if upload fails
     */
    String uploadFirmwareFile(MultipartFile file, String deviceType, String subdirectory) throws CloudStorageException;

    /**
     * Download a firmware file from cloud storage
     * 
     * @param storagePath The path where the file is stored
     * @return InputStream of the file content
     * @throws CloudStorageException if download fails
     */
    InputStream downloadFirmwareFile(String storagePath) throws CloudStorageException;

    /**
     * Delete a firmware file from cloud storage
     * 
     * @param storagePath The path where the file is stored
     * @throws CloudStorageException if deletion fails
     */
    void deleteFirmwareFile(String storagePath) throws CloudStorageException;

    /**
     * Check if a file exists in cloud storage
     * 
     * @param storagePath The path where the file is stored
     * @return true if file exists, false otherwise
     */
    boolean fileExists(String storagePath);

    /**
     * Get the file size of a stored file
     * 
     * @param storagePath The path where the file is stored
     * @return File size in bytes
     * @throws CloudStorageException if operation fails
     */
    long getFileSize(String storagePath) throws CloudStorageException;

    /**
     * Generate a presigned URL for direct file access
     * 
     * @param storagePath       The path where the file is stored
     * @param expirationMinutes URL expiration time in minutes
     * @return Presigned URL
     * @throws CloudStorageException if URL generation fails
     */
    String generatePresignedUrl(String storagePath, int expirationMinutes) throws CloudStorageException;

    /**
     * List all firmware files for a specific device type
     * 
     * @param deviceType RSU or OBU
     * @return List of storage paths
     * @throws CloudStorageException if listing fails
     */
    List<String> listFirmwareFiles(String deviceType) throws CloudStorageException;

    /**
     * Get the storage provider name
     * 
     * @return Provider name (e.g., "local", "gcp", "aws-s3")
     */
    String getProviderName();

    /**
     * Validate a firmware file before upload
     * 
     * @param file       The file to validate
     * @param deviceType The device type (RSU/OBU)
     * @throws CloudStorageException if validation fails
     */
    void validateFile(MultipartFile file, String deviceType) throws CloudStorageException;

    /**
     * Calculate checksum for a file
     * 
     * @param file The file to calculate checksum for
     * @return SHA-256 checksum as hex string
     * @throws CloudStorageException if calculation fails
     */
    String calculateChecksum(MultipartFile file) throws CloudStorageException;

    /**
     * Calculate file hash for a file (same as checksum but with different naming
     * for clarity)
     * 
     * @param file The file to calculate hash for
     * @return SHA-256 file hash as hex string
     * @throws CloudStorageException if calculation fails
     */
    String calculateFileHash(MultipartFile file) throws CloudStorageException;

    /**
     * Get storage type identifier
     * 
     * @return Storage type (e.g., "LOCAL", "GCP", "AWS")
     */
    String getStorageType();
}
