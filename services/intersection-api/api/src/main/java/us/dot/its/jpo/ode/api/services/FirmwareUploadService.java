package us.dot.its.jpo.ode.api.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrlRequest;
import us.dot.its.jpo.ode.api.repositories.RsuModelRepository;
import us.dot.its.jpo.ode.api.storage.ObjectStorageProperties;
import us.dot.its.jpo.ode.api.storage.ObjectStorageService;

/** Validates firmware upload business rules before delegating to object storage. */
@Service
@RequiredArgsConstructor
public class FirmwareUploadService {
    private final RsuModelRepository rsuModelRepository;
    private final ObjectStorageService objectStorageService;
    private final ObjectStorageProperties objectStorageProperties;

    public SignedUploadUrl createFirmwareSignedUploadUrl(SignedUploadUrlRequest request) {
        String vendorName = request.getVendorName().trim();
        String modelName = request.getModelName().trim();
        if (rsuModelRepository.findByNameAndManufacturerName(modelName, vendorName).isEmpty()) {
            throw new EntityNotFoundException(
                    "RSU model '" + modelName + "' was not found for vendor '" + vendorName + "'");
        }

        long maxUploadBytes = objectStorageProperties.getMaxUploadSize().toBytes();
        if (request.getContentLength() > maxUploadBytes) {
            throw new IllegalArgumentException(
                    "content_length must not exceed " + maxUploadBytes + " bytes");
        }

        return objectStorageService.createSignedUploadUrl(request);
    }
}
