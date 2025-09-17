package us.dot.its.jpo.ode.api.services.firmware;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Local file system implementation of CloudStorageService
 * Stores firmware files in the local file system
 */
@Service
public class LocalFileStorageService extends AbstractCloudStorageService {

    @Value("${firmware.storage.local.base-path:/tmp/firmware}")
    private String basePath;

    @Value("${firmware.storage.local.max-file-size:100MB}")
    private DataSize maxFileSize;

    @Override
    public String uploadFirmwareFile(MultipartFile file, String deviceType, String subdirectory)
            throws CloudStorageException {
        try {
            // Validate file using common validation
            validateFile(file, deviceType, maxFileSize.toBytes());

            // Create directory structure: basePath/deviceType/subdirectory/YYYY/MM/DD/
            String datePath = generateDatePath();
            Path uploadPath = Paths.get(basePath, deviceType.toLowerCase(), subdirectory, datePath);

            // Create directories if they don't exist
            Files.createDirectories(uploadPath);

            // Generate unique filename using common method
            String uniqueFilename = generateUniqueFilename(file.getOriginalFilename());
            Path filePath = uploadPath.resolve(uniqueFilename);

            // Save file
            try (InputStream inputStream = file.getInputStream();
                    OutputStream outputStream = Files.newOutputStream(filePath)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            // Return relative path for storage in database using common method
            return buildStoragePath(deviceType, subdirectory, datePath, uniqueFilename);

        } catch (IOException e) {
            throw new CloudStorageException("Failed to upload firmware file: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream downloadFirmwareFile(String storagePath) throws CloudStorageException {
        try {
            Path filePath = Paths.get(basePath, storagePath);

            if (!Files.exists(filePath)) {
                throw new CloudStorageException("Firmware file not found: " + storagePath);
            }

            return Files.newInputStream(filePath);

        } catch (IOException e) {
            throw new CloudStorageException("Failed to download firmware file: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFirmwareFile(String storagePath) throws CloudStorageException {
        try {
            Path filePath = Paths.get(basePath, storagePath);

            if (Files.exists(filePath)) {
                Files.delete(filePath);

                // Try to remove empty parent directories
                Path parent = filePath.getParent();
                while (parent != null && !parent.equals(Paths.get(basePath))) {
                    try {
                        Files.delete(parent);
                        parent = parent.getParent();
                    } catch (IOException e) {
                        // Directory not empty or other error, stop trying to delete parents
                        break;
                    }
                }
            }

        } catch (IOException e) {
            throw new CloudStorageException("Failed to delete firmware file: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean fileExists(String storagePath) {
        try {
            Path filePath = Paths.get(basePath, storagePath);
            return Files.exists(filePath);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long getFileSize(String storagePath) throws CloudStorageException {
        try {
            Path filePath = Paths.get(basePath, storagePath);
            return Files.size(filePath);
        } catch (IOException e) {
            throw new CloudStorageException("Failed to get file size: " + e.getMessage(), e);
        }
    }

    @Override
    public String getStorageType() {
        return "LOCAL";
    }

    @Override
    public void validateFile(MultipartFile file, String deviceType) throws CloudStorageException {
        // Use common validation with local max file size
        validateFile(file, deviceType, maxFileSize.toBytes());
    }

    @Override
    public List<String> listFirmwareFiles(String deviceType) throws CloudStorageException {
        try {
            Path devicePath = Paths.get(basePath, deviceType.toLowerCase());
            if (!Files.exists(devicePath)) {
                return new ArrayList<>();
            }

            List<String> files = new ArrayList<>();
            Files.walk(devicePath)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        String relativePath = devicePath.relativize(path).toString();
                        files.add(relativePath);
                    });

            return files;
        } catch (IOException e) {
            throw new CloudStorageException("Failed to list firmware files: " + e.getMessage(), e);
        }
    }

    @Override
    public String generatePresignedUrl(String storagePath, int expirationMinutes) throws CloudStorageException {
        // For local storage, we can't generate presigned URLs
        // Return the local file path or a file:// URL
        Path filePath = Paths.get(basePath, storagePath);
        return "file://" + filePath.toAbsolutePath().toString();
    }

    @Override
    public String getProviderName() {
        return "Local File System";
    }
}