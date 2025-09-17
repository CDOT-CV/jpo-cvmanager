package us.dot.its.jpo.ode.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import us.dot.its.jpo.ode.api.services.firmware.CloudStorageService;
import us.dot.its.jpo.ode.api.services.firmware.LocalFileStorageService;
import us.dot.its.jpo.ode.api.services.firmware.GcpCloudStorageService;

/**
 * Configuration class for firmware storage based on environment variables
 * Supports local, GCP, AWS, and Azure storage providers
 */
@Configuration
public class FirmwareStorageConfig {

    @Value("${firmware.storage.type:local}")
    private String storageType;

    @Value("${firmware.storage.local.base-path:/tmp/firmware}")
    private String localBasePath;

    @Value("${firmware.storage.local.max-file-size:100MB}")
    private String localMaxFileSize;

    @Value("${firmware.storage.gcp.bucket-name:cv-manager-firmware}")
    private String gcpBucketName;

    @Value("${firmware.storage.gcp.project-id:}")
    private String gcpProjectId;

    @Value("${firmware.storage.gcp.service-account-key:}")
    private String gcpServiceAccountKey;

    @Value("${firmware.storage.gcp.base-path:firmware/}")
    private String gcpBasePath;

    @Value("${firmware.storage.aws.bucket-name:cv-manager-firmware}")
    private String awsBucketName;

    @Value("${firmware.storage.aws.region:us-east-1}")
    private String awsRegion;

    @Value("${firmware.storage.aws.access-key:}")
    private String awsAccessKey;

    @Value("${firmware.storage.aws.secret-key:}")
    private String awsSecretKey;

    @Value("${firmware.storage.aws.base-path:firmware/}")
    private String awsBasePath;

    @Value("${firmware.storage.azure.account-name:}")
    private String azureAccountName;

    @Value("${firmware.storage.azure.account-key:}")
    private String azureAccountKey;

    @Value("${firmware.storage.azure.container-name:firmware}")
    private String azureContainerName;

    @Value("${firmware.storage.azure.base-path:firmware/}")
    private String azureBasePath;

    /**
     * Configure the appropriate CloudStorageService based on environment variables
     * 
     * @return Configured CloudStorageService implementation
     */
    @Bean
    @Primary
    public CloudStorageService cloudStorageService() {
        switch (storageType.toLowerCase()) {
            case "gcp":
                return createGcpStorageService();
            case "aws":
                return createAwsStorageService();
            case "azure":
                return createAzureStorageService();
            case "local":
            default:
                return createLocalStorageService();
        }
    }

    /**
     * Create local file storage service
     */
    private CloudStorageService createLocalStorageService() {
        LocalFileStorageService service = new LocalFileStorageService();
        // Set properties via reflection or constructor if needed
        return service;
    }

    /**
     * Create GCP Cloud Storage service
     */
    private CloudStorageService createGcpStorageService() {
        try {
            GcpCloudStorageService gcpService = new GcpCloudStorageService();
            // Set properties via reflection or constructor if needed
            if (!gcpService.isConfigured()) {
                System.out.println("GCP storage not properly configured, falling back to local storage");
                return createLocalStorageService();
            }
            return gcpService;
        } catch (Exception e) {
            System.out
                    .println("Failed to create GCP storage service, falling back to local storage: " + e.getMessage());
            return createLocalStorageService();
        }
    }

    /**
     * Create AWS S3 storage service
     * TODO: Implement AwsS3StorageService
     */
    private CloudStorageService createAwsStorageService() {
        // For now, fall back to local storage
        // In production, implement AwsS3StorageService
        System.out.println("AWS S3 storage requested but not implemented, falling back to local storage");
        return createLocalStorageService();
    }

    /**
     * Create Azure Blob Storage service
     * TODO: Implement AzureBlobStorageService
     */
    private CloudStorageService createAzureStorageService() {
        // For now, fall back to local storage
        // In production, implement AzureBlobStorageService
        System.out.println("Azure Blob storage requested but not implemented, falling back to local storage");
        return createLocalStorageService();
    }
}
