package us.dot.its.jpo.ode.api.services.firmware;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import us.dot.its.jpo.ode.api.models.firmware.FirmwareStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for integrating with the Firmware Scheduler
 * Retrieves active and queued upgrades from the /list_active_upgrades endpoint
 */
@Service
public class FirmwareSchedulerService {

    @Value("${firmware.scheduler.base-url:http://localhost:8080}")
    private String schedulerBaseUrl;

    @Value("${firmware.scheduler.timeout:30000}")
    private int timeoutMs;

    private final RestTemplate restTemplate;

    public FirmwareSchedulerService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Get active and queued upgrades from the Firmware Scheduler
     * 
     * @param deviceType RSU or OBU
     * @return List of firmware statuses
     * @throws FirmwareServiceException if request fails
     */
    public List<FirmwareStatus> getActiveUpgrades(String deviceType) throws FirmwareServiceException {
        try {
            String url = schedulerBaseUrl + "/list_active_upgrades";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            // Add device type filter if needed
            if (deviceType != null && !deviceType.isEmpty()) {
                url += "?device_type=" + deviceType.toLowerCase();
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseSchedulerResponse(response.getBody(), deviceType);
            } else {
                throw new FirmwareServiceException("Failed to get active upgrades from scheduler");
            }

        } catch (Exception e) {
            throw new FirmwareServiceException("Error communicating with firmware scheduler: " + e.getMessage(), e);
        }
    }

    /**
     * Parse the response from the firmware scheduler
     * 
     * @param response   Response body from scheduler
     * @param deviceType Device type filter
     * @return List of firmware statuses
     */
    @SuppressWarnings("unchecked")
    private List<FirmwareStatus> parseSchedulerResponse(Map<String, Object> response, String deviceType) {
        List<FirmwareStatus> statuses = new ArrayList<>();

        try {
            List<Map<String, Object>> upgrades = (List<Map<String, Object>>) response.get("upgrades");
            if (upgrades != null) {
                for (Map<String, Object> upgrade : upgrades) {
                    FirmwareStatus status = parseUpgradeToStatus(upgrade, deviceType);
                    if (status != null) {
                        statuses.add(status);
                    }
                }
            }
        } catch (Exception e) {
            // Log error but continue processing
            System.err.println("Error parsing scheduler response: " + e.getMessage());
        }

        return statuses;
    }

    /**
     * Parse individual upgrade entry to FirmwareStatus
     * 
     * @param upgrade    Upgrade data from scheduler
     * @param deviceType Device type filter
     * @return FirmwareStatus object
     */
    @SuppressWarnings("unchecked")
    private FirmwareStatus parseUpgradeToStatus(Map<String, Object> upgrade, String deviceType) {
        try {
            String deviceIp = (String) upgrade.get("device_ip");
            String currentVersion = (String) upgrade.get("current_version");
            String targetVersion = (String) upgrade.get("target_version");
            String status = (String) upgrade.get("status");
            String errorMessage = (String) upgrade.get("error_message");
            Integer progress = (Integer) upgrade.get("progress_percentage");

            // Determine device type
            FirmwareStatus.DeviceType type = FirmwareStatus.DeviceType.RSU; // Default
            if (deviceType != null) {
                type = FirmwareStatus.DeviceType.valueOf(deviceType.toUpperCase());
            }

            // Map status string to enum
            FirmwareStatus.UpgradeStatus upgradeStatus = mapStatusString(status);

            FirmwareStatus firmwareStatus = new FirmwareStatus(
                    null, // rsuId - will be set based on device type
                    null, // obuSn - will be set based on device type
                    type,
                    currentVersion,
                    targetVersion,
                    upgradeStatus);

            if (errorMessage != null) {
                firmwareStatus.setErrorMessage(errorMessage);
            }

            if (progress != null) {
                firmwareStatus.setProgressPercentage(progress);
            }

            // Parse last updated timestamp
            String lastUpdatedStr = (String) upgrade.get("last_updated");
            if (lastUpdatedStr != null) {
                try {
                    LocalDateTime lastUpdated = LocalDateTime.parse(lastUpdatedStr,
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    firmwareStatus.setLastUpdated(lastUpdated);
                } catch (Exception e) {
                    // Use current time if parsing fails
                    firmwareStatus.setLastUpdated(LocalDateTime.now());
                }
            }

            return firmwareStatus;

        } catch (Exception e) {
            System.err.println("Error parsing upgrade entry: " + e.getMessage());
            return null;
        }
    }

    /**
     * Map status string from scheduler to FirmwareStatus.UpgradeStatus enum
     * 
     * @param status Status string from scheduler
     * @return Mapped enum value
     */
    private FirmwareStatus.UpgradeStatus mapStatusString(String status) {
        if (status == null) {
            return FirmwareStatus.UpgradeStatus.IDLE;
        }

        switch (status.toLowerCase()) {
            case "idle":
                return FirmwareStatus.UpgradeStatus.IDLE;
            case "downloading":
                return FirmwareStatus.UpgradeStatus.DOWNLOADING;
            case "installing":
                return FirmwareStatus.UpgradeStatus.INSTALLING;
            case "completed":
                return FirmwareStatus.UpgradeStatus.COMPLETED;
            case "failed":
                return FirmwareStatus.UpgradeStatus.FAILED;
            case "cancelled":
                return FirmwareStatus.UpgradeStatus.CANCELLED;
            default:
                return FirmwareStatus.UpgradeStatus.IDLE;
        }
    }

    /**
     * Check if firmware scheduler is available
     * 
     * @return true if scheduler is reachable
     */
    public boolean isSchedulerAvailable() {
        try {
            String url = schedulerBaseUrl + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
