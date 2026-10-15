package us.dot.its.jpo.ode.api.models.postgres.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** An RSU firmware upload intent and its verification evidence. */
@Getter
@Setter
@Entity
@Table(name = "firmware_uploads")
public class FirmwareUpload extends FirmwareUploadRecord {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model", nullable = false)
    private RsuModel model;

    @Size(max = 128)
    @NotNull
    @Column(name = "version", nullable = false, length = 128)
    private String version;
}
