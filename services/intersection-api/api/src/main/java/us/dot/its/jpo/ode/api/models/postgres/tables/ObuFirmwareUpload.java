package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** An OBU firmware upload intent and its verification evidence. */
@Getter
@Setter
@Entity
@Table(name = "obu_firmware_uploads")
public class ObuFirmwareUpload extends FirmwareUploadRecord {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manufacturer", nullable = false)
    private Manufacturer manufacturer;

    @Valid
    @NotNull
    @Embedded
    private ObuFirmwareFileMetadata metadata;
}
