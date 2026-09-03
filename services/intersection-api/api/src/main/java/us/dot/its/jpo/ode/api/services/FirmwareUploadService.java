package us.dot.its.jpo.ode.api.services;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpload;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadUrlRequest;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadVerification;
import us.dot.its.jpo.ode.api.models.storage.ObjectChecksum;
import us.dot.its.jpo.ode.api.models.storage.ObjectStorageLocation;
import us.dot.its.jpo.ode.api.models.storage.ObjectUploadRequest;
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.StoredObjectMetadata;
import us.dot.its.jpo.ode.api.repositories.FirmwareUploadRepository;
import us.dot.its.jpo.ode.api.repositories.RsuModelRepository;
import us.dot.its.jpo.ode.api.storage.ObjectStorageProperties;
import us.dot.its.jpo.ode.api.storage.ObjectStorageService;
import us.dot.its.jpo.ode.api.storage.ObjectStorageServiceRegistry;

/**
 * Coordinates the firmware upload lifecycle across PostgreSQL and the selected
 * object-storage provider
 */
@Service
@RequiredArgsConstructor
public class FirmwareUploadService {
    private final RsuModelRepository rsuModelRepository;
    private final FirmwareUploadRepository firmwareUploadRepository;
    private final ObjectStorageServiceRegistry objectStorageServices;
    private final ObjectStorageProperties objectStorageProperties;

    public FirmwareUploadUrl createFirmwareSignedUploadUrl(FirmwareUploadUrlRequest request, String createdBy) {
        // Resolve the model from trusted database records instead of accepting an
        // arbitrary vendor/model path from the client
        String vendorName = request.getVendorName().trim();
        String modelName = request.getModelName().trim();
        RsuModel model = rsuModelRepository.findByNameAndManufacturerName(modelName, vendorName)
                .orElseThrow(() -> new EntityNotFoundException(
                        "RSU model '" + modelName + "' was not found for vendor '" + vendorName + "'"));

        if (request.getContentLength() == null || request.getContentLength() <= 0) {
            throw new IllegalArgumentException("content_length must be greater than zero");
        }
        long maxUploadBytes = objectStorageProperties.getMaxUploadSize().toBytes();
        if (request.getContentLength() > maxUploadBytes) {
            throw new IllegalArgumentException(
                    "content_length must not exceed " + maxUploadBytes + " bytes");
        }

        // Build a provider-neutral upload request. The selected provider validates
        // whether it supports the requested checksum algorithm and encoding
        String checksumAlgorithm = request.getChecksumAlgorithm().trim().toUpperCase(Locale.ROOT);
        ObjectChecksum expectedChecksum = new ObjectChecksum(checksumAlgorithm, request.getChecksum().trim());
        String objectName = buildObjectName(request);
        ObjectStorageService objectStorageService = objectStorageServices.getActiveService();
        SignedUploadUrl signedUrl = objectStorageService.createSignedUploadUrl(new ObjectUploadRequest(
                objectName, request.getContentLength(), request.getContentType().trim(), expectedChecksum));
        ObjectStorageLocation location = signedUrl.location();
        validateSignedLocation(objectStorageService, location, objectName);
        Instant now = Instant.now();

        // Persist the intent only after signing succeeds, so every PENDING row has a
        // usable set of upload instructions associated with it
        FirmwareUpload upload = new FirmwareUpload();
        upload.setId(UUID.randomUUID());
        upload.setModel(model);
        upload.setVersion(request.getVersion().trim());
        upload.setFileName(request.getFileName().trim());
        upload.setContentType(request.getContentType().trim());
        upload.setStorageProvider(location.provider());
        upload.setStorageContainer(location.container());
        upload.setObjectName(location.objectName());
        upload.setExpectedSize(request.getContentLength());
        upload.setChecksumAlgorithm(checksumAlgorithm);
        upload.setExpectedChecksum(expectedChecksum.value());
        upload.setStatus(FirmwareUploadStatus.PENDING);
        upload.setCreatedBy(normalizeCreatedBy(createdBy));
        upload.setCreatedAt(now);
        upload.setExpiresAt(signedUrl.expiresAt());
        firmwareUploadRepository.save(upload);

        return new FirmwareUploadUrl(upload.getId(), signedUrl.uploadUrl(), signedUrl.method(),
                location.objectName(), signedUrl.expiresAt(), signedUrl.requiredHeaders());
    }

    public FirmwareUploadVerification completeFirmwareUpload(UUID uploadId) {
        FirmwareUpload upload = firmwareUploadRepository.findById(uploadId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Firmware upload '" + uploadId + "' was not found"));

        if (upload.getStatus() == FirmwareUploadStatus.VERIFIED) {
            return toVerification(upload);
        }

        // Route completion through the cloud provider recorded when the URL was
        // issued. This remains valid even if the application's active provider later
        // changes
        ObjectStorageService objectStorageService = objectStorageServices.getService(upload.getStorageProvider());
        ObjectStorageLocation location = new ObjectStorageLocation(
                upload.getStorageProvider(), upload.getStorageContainer(), upload.getObjectName());
        StoredObjectMetadata metadata = objectStorageService
                .getObjectMetadata(location, upload.getChecksumAlgorithm())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "The firmware object has not been uploaded"));

        // Verification requires both the expected byte size and the exact checksum
        // Comparing the algorithm prevents equal looking values from different hash
        // formats from being treated as equivalent
        if (metadata.contentLength() != upload.getExpectedSize()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Uploaded object size does not match content_length");
        }
        if (metadata.checksum() == null
                || !upload.getChecksumAlgorithm().equalsIgnoreCase(metadata.checksum().algorithm())
                || !upload.getExpectedChecksum().equals(metadata.checksum().value())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Uploaded object checksum does not match the expected checksum");
        }

        // Retain the provider's observed values so the verified record identifies the
        // exact stored object version that was checked
        upload.setStatus(FirmwareUploadStatus.VERIFIED);
        upload.setObservedChecksum(metadata.checksum().value());
        upload.setProviderObjectVersion(metadata.providerObjectVersion());
        upload.setVerifiedAt(Instant.now());
        firmwareUploadRepository.save(upload);
        return toVerification(upload);
    }

    private FirmwareUploadVerification toVerification(FirmwareUpload upload) {
        return new FirmwareUploadVerification(upload.getId(), upload.getStatus(), upload.getObjectName(),
                upload.getExpectedSize(), upload.getChecksumAlgorithm(), upload.getObservedChecksum(),
                upload.getProviderObjectVersion(), upload.getVerifiedAt());
    }

    private String buildObjectName(FirmwareUploadUrlRequest request) {
        return String.join("/",
                validatePathSegment(request.getVendorName(), "vendor_name"),
                validatePathSegment(request.getModelName(), "model_name"),
                validatePathSegment(request.getVersion(), "version"),
                validatePathSegment(request.getFileName(), "file_name"));
    }

    private String validatePathSegment(String value, String fieldName) {
        String segment = value == null ? "" : value.trim();
        if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                || segment.indexOf('/') >= 0 || segment.indexOf('\\') >= 0
                || segment.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(fieldName + " must be a valid object path segment");
        }
        return segment;
    }

    private void validateSignedLocation(
            ObjectStorageService service, ObjectStorageLocation location, String requestedObjectName) {
        if (location == null
                || !service.providerName().equalsIgnoreCase(location.provider())
                || location.container() == null || location.container().isBlank()
                || !requestedObjectName.equals(location.objectName())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Object storage returned an invalid upload location");
        }
    }

    private String normalizeCreatedBy(String createdBy) {
        if (createdBy == null || createdBy.isBlank()) {
            return "unknown";
        }
        String normalized = createdBy.trim();
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }
}
