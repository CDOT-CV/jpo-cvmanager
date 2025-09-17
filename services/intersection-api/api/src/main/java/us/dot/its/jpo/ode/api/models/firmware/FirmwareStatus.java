package us.dot.its.jpo.ode.api.models.firmware;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "firmware_statuses")
@Data
@AllArgsConstructor
public class FirmwareStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty("status_id")
    private Integer statusId;

    @JsonProperty("rsu_id")
    private Integer rsuId;

    @JsonProperty("obu_sn")
    private String obuSn;

    @JsonProperty("device_type")
    @Enumerated(EnumType.STRING)
    private DeviceType deviceType;

    @JsonProperty("current_version")
    private String currentVersion;

    @JsonProperty("target_version")
    private String targetVersion;

    @JsonProperty("upgrade_status")
    @Enumerated(EnumType.STRING)
    private UpgradeStatus upgradeStatus;

    @JsonProperty("last_updated")
    private LocalDateTime lastUpdated;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("progress_percentage")
    private Integer progressPercentage = 0;

    @JsonProperty("estimated_completion")
    private LocalDateTime estimatedCompletion;

    @JsonProperty("request_datetime")
    private LocalDateTime requestDatetime;

    @JsonProperty("origin_ip")
    private String originIp;

    // Custom constructor for creating new firmware statuses
    public FirmwareStatus(Integer rsuId, String obuSn, DeviceType deviceType, String currentVersion,
            String targetVersion, UpgradeStatus upgradeStatus) {
        this();
        this.rsuId = rsuId;
        this.obuSn = obuSn;
        this.deviceType = deviceType;
        this.currentVersion = currentVersion;
        this.targetVersion = targetVersion;
        this.upgradeStatus = upgradeStatus;
    }

    // Initialize last updated in default constructor
    public FirmwareStatus() {
        this.lastUpdated = LocalDateTime.now();
    }

    public enum UpgradeStatus {
        IDLE, DOWNLOADING, INSTALLING, COMPLETED, FAILED, CANCELLED
    }

    public enum DeviceType {
        RSU, OBU
    }
}
