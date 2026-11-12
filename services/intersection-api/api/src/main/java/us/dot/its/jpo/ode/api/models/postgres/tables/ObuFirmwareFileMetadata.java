package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Details encoded in an OBU firmware file name, stored with both the upload and
 * the catalog image. Manufacturer-specific details are null when a naming
 * convention does not include them.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class ObuFirmwareFileMetadata {
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 16)
    private ObuFirmwareDeviceType deviceType;

    @Size(max = 128)
    @NotNull
    @Column(name = "release_version", nullable = false, length = 128)
    private String releaseVersion;

    @Size(max = 128)
    @Column(name = "variant", length = 128)
    private String variant;

    @Column(name = "writable_system_partition")
    private Boolean writableSystemPartition;

    @Column(name = "secure_boot")
    private Boolean secureBoot;
}
