package us.dot.its.jpo.ode.api.services;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import us.dot.its.jpo.ode.api.mappers.FirmwareUploadMapper;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpload;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.models.storage.StoredObjectMetadata;
import us.dot.its.jpo.ode.api.repositories.FirmwareImageRepository;
import us.dot.its.jpo.ode.api.repositories.FirmwareUploadRepository;

/** Commits verification and image registration together, without cloud calls in the transaction. */
@Service
@RequiredArgsConstructor
public class FirmwareRegistrationService {
    private final FirmwareUploadRepository uploads;
    private final FirmwareImageRepository images;
    private final FirmwareUploadMapper mapper;

    @Transactional
    public FirmwareUpload register(UUID uploadId, StoredObjectMetadata metadata) {
        // Serialize repeated completion calls for the same upload.
        FirmwareUpload upload = uploads.findByIdForUpdate(uploadId)
                .orElseThrow(() -> new EntityNotFoundException("Firmware upload '" + uploadId + "' was not found"));
        var existing = images.findByModelIdAndVersion(upload.getModel().getId(), upload.getVersion());
        if (existing.isPresent() && (existing.get().getVerifiedUpload() == null
                || !uploadId.equals(existing.get().getVerifiedUpload().getId()))) {
            throw new FirmwareVersionAlreadyExistsException("Firmware already exists for this model and version");
        }
        if (upload.getStatus() != FirmwareUploadStatus.VERIFIED) {
            if (metadata == null || metadata.contentLength() != upload.getExpectedSize()
                    || metadata.checksum() == null
                    || !upload.getChecksumAlgorithm().equalsIgnoreCase(metadata.checksum().algorithm())
                    || !upload.getExpectedChecksum().equals(metadata.checksum().value())) {
                throw new FirmwareUploadVerificationException("Firmware metadata does not match the upload intent");
            }
            upload.setStatus(FirmwareUploadStatus.VERIFIED);
            upload.setObservedChecksum(metadata.checksum().value());
            upload.setProviderObjectVersion(metadata.providerObjectVersion());
            upload.setVerifiedAt(Instant.now());
            upload.setFinishedAt(upload.getVerifiedAt());
            upload.setFailureReason(null);
            uploads.save(upload);
        }
        if (existing.isEmpty()) {
            images.saveAndFlush(mapper.toFirmwareImage(upload));
        }
        return upload;
    }
}
