package us.dot.its.jpo.ode.api.services.firmware;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Abstract base class for cloud storage services
 * Contains common functionality shared between different storage
 * implementations
 */
public abstract class AbstractCloudStorageService implements CloudStorageService {

    /**
     * Common file validation logic
     * 
     * @param file        The file to validate
     * @param deviceType  The device type (RSU/OBU)
     * @param maxFileSize Maximum allowed file size in bytes
     * @throws CloudStorageException if validation fails
     */
    protected void validateFile(MultipartFile file, String deviceType, long maxFileSize) throws CloudStorageException {
        if (file == null || file.isEmpty()) {
            throw new CloudStorageException("File is empty or null");
        }

        if (file.getSize() > maxFileSize) {
            throw new CloudStorageException("File size exceeds maximum allowed size: " + maxFileSize + " bytes");
        }

        // Additional validation can be added here (file type, device type specific
        // checks, etc.)
    }

    /**
     * Common checksum calculation using SHA-256
     * 
     * @param file The file to calculate checksum for
     * @return SHA-256 checksum as hex string
     * @throws CloudStorageException if calculation fails
     */
    @Override
    public String calculateChecksum(MultipartFile file) throws CloudStorageException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream inputStream = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            byte[] hash = digest.digest();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException | IOException e) {
            throw new CloudStorageException("Failed to calculate checksum: " + e.getMessage(), e);
        }
    }

    /**
     * Calculate file hash using SHA-256 (same as checksum but with different naming
     * for clarity)
     * 
     * @param file The file to calculate hash for
     * @return SHA-256 file hash as hex string
     * @throws CloudStorageException if calculation fails
     */
    @Override
    public String calculateFileHash(MultipartFile file) throws CloudStorageException {
        // For now, file hash is the same as checksum
        // In the future, this could be a different algorithm if needed
        return calculateChecksum(file);
    }

    /**
     * Generate a unique filename to avoid conflicts
     * 
     * @param originalFilename The original filename
     * @return Unique filename with UUID prefix
     */
    protected String generateUniqueFilename(String originalFilename) {
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + fileExtension;
    }

    /**
     * Generate a date-based path structure
     * 
     * @return Date path in format YYYY/MM/DD
     */
    protected String generateDatePath() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
    }

    /**
     * Build storage path for a file
     * 
     * @param deviceType   The device type (RSU/OBU)
     * @param subdirectory The subdirectory
     * @param datePath     The date path
     * @param filename     The filename
     * @return Complete storage path
     */
    protected String buildStoragePath(String deviceType, String subdirectory, String datePath, String filename) {
        return String.format("%s/%s/%s/%s",
                deviceType.toLowerCase(),
                subdirectory,
                datePath,
                filename);
    }

    /**
     * Extract file extension from filename
     * 
     * @param filename The filename
     * @return File extension including the dot, or empty string if none
     */
    protected String extractFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf("."));
        }
        return "";
    }

    /**
     * Validate that a string is not null or empty
     * 
     * @param value     The value to validate
     * @param fieldName The name of the field for error messages
     * @throws CloudStorageException if validation fails
     */
    protected void validateNotEmpty(String value, String fieldName) throws CloudStorageException {
        if (value == null || value.trim().isEmpty()) {
            throw new CloudStorageException(fieldName + " cannot be null or empty");
        }
    }

    /**
     * Validate that a number is positive
     * 
     * @param value     The value to validate
     * @param fieldName The name of the field for error messages
     * @throws CloudStorageException if validation fails
     */
    protected void validatePositive(long value, String fieldName) throws CloudStorageException {
        if (value <= 0) {
            throw new CloudStorageException(fieldName + " must be positive");
        }
    }
}
