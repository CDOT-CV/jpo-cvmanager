package us.dot.its.jpo.ode.api.services.firmware;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Factory for creating the appropriate storage service implementation
 * based on configuration
 */
@Component
public class StorageServiceFactory {

    @Value("${firmware.storage.type:LOCAL}")
    private String storageType;

    @Autowired
    private LocalFileStorageService localFileStorageService;

    @Autowired
    private GcpCloudStorageService gcpCloudStorageService;

    /**
     * Get the configured storage service implementation
     * 
     * @return CloudStorageService implementation
     * @throws IllegalArgumentException if storage type is not supported
     */
    public CloudStorageService getStorageService() {
        switch (storageType.toUpperCase()) {
            case "LOCAL":
                return localFileStorageService;
            case "GCP":
            case "GOOGLE_CLOUD":
                return gcpCloudStorageService;
            default:
                throw new IllegalArgumentException("Unsupported storage type: " + storageType);
        }
    }

    /**
     * Get the current storage type
     * 
     * @return Storage type string
     */
    public String getStorageType() {
        return storageType.toUpperCase();
    }
}
