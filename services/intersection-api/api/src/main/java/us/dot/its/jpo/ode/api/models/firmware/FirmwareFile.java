package us.dot.its.jpo.ode.api.models.firmware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturers;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "firmware_images")
@Data
@AllArgsConstructor
public class FirmwareFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty("firmware_id")
    private Integer firmwareId;

    @NotBlank(message = "Name is required")
    @Size(max = 128, message = "Name must not exceed 128 characters")
    @JsonProperty("name")
    private String name;

    @NotNull(message = "Model is required")
    @JsonProperty("model")
    private Integer model;

    @NotNull(message = "Manufacturer is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_id")
    @JsonProperty("manufacturer")
    private Manufacturers manufacturer;

    @NotBlank(message = "Install package is required")
    @Size(max = 128, message = "Install package must not exceed 128 characters")
    @JsonProperty("install_package")
    private String installPackage;

    @NotBlank(message = "Version is required")
    @Size(max = 128, message = "Version must not exceed 128 characters")
    @JsonProperty("version")
    private String version;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @JsonProperty("description")
    private String description;

    @NotBlank(message = "Checksum is required")
    @Size(max = 64, message = "Checksum must not exceed 64 characters")
    @JsonProperty("checksum")
    private String checksum;

    @NotBlank(message = "Storage path is required")
    @JsonIgnore
    private String storagePath;

    @JsonProperty("upload_date")
    private LocalDateTime uploadDate;

    @JsonProperty("created_by")
    private String createdBy;

    @JsonProperty("is_active")
    private Boolean isActive = true;

    @JsonProperty("device_type")
    @Enumerated(EnumType.STRING)
    private DeviceType deviceType;

    @JsonProperty("file_hash")
    private String fileHash;

    @JsonProperty("file_size")
    private Long fileSize;

    @OneToMany(mappedBy = "toFirmware", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonProperty("rules")
    private List<FirmwareRule> rules;

    // Custom constructor for creating new firmware files
    public FirmwareFile(String name, Integer model, Manufacturers manufacturer, String installPackage, String version,
            String description, String checksum, String storagePath, String createdBy,
            DeviceType deviceType, String fileHash, Long fileSize) {
        this();
        this.name = name;
        this.model = model;
        this.manufacturer = manufacturer;
        this.installPackage = installPackage;
        this.version = version;
        this.description = description;
        this.checksum = checksum;
        this.storagePath = storagePath;
        this.createdBy = createdBy;
        this.deviceType = deviceType;
        this.fileHash = fileHash;
        this.fileSize = fileSize;
    }

    // Initialize upload date in default constructor
    public FirmwareFile() {
        this.uploadDate = LocalDateTime.now();
    }

    public enum DeviceType {
        RSU, OBU
    }
}
