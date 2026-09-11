package us.dot.its.jpo.ode.api.services;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import us.dot.its.jpo.ode.api.repositories.FirmwareImageRepository;
import us.dot.its.jpo.ode.api.repositories.FirmwareUpgradeRuleRepository;
import us.dot.its.jpo.ode.api.repositories.FirmwareUploadRepository;
import us.dot.its.jpo.ode.api.repositories.MaxRetryLimitReachedInstanceRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareImage;
import us.dot.its.jpo.ode.api.storage.ObjectStorageServiceRegistry;

@Service
@RequiredArgsConstructor
public class FirmwareDeletionService {
    private final ObjectStorageServiceRegistry storage;
    private final FirmwareUploadRepository uploads;
    private final FirmwareImageRepository images;
    private final FirmwareUpgradeRuleRepository rules;
    private final RsuRepository rsus;
    private final MaxRetryLimitReachedInstanceRepository failureHistory;

    @Transactional
    public void delete(String objectId, String providerObjectVersion) {
        if (providerObjectVersion == null || providerObjectVersion.isBlank()) {
            throw new IllegalArgumentException("The listed object version is required for deletion");
        }
        String[] identity;
        try {
            identity = new String(Base64.getUrlDecoder().decode(objectId), StandardCharsets.UTF_8).split("\n", 2);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid firmware object ID");
        }
        if (identity.length != 2 || identity[1].isBlank() || identity[1].endsWith("/")
                || identity[1].chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid firmware object ID");
        }
        String objectName = identity[1];
        if (objectName.split("/", 2)[0].toLowerCase(Locale.ROOT).equals("ota")) {
            throw new IllegalArgumentException("OBU firmware cannot be deleted through the RSU firmware API");
        }

        var service = storage.getActiveService();
        if (!service.providerName().equals(identity[0])) {
            throw new FirmwareDeletionConflictException("The storage provider changed. Refresh the firmware table.");
        }
        var location = service.getLocation(objectName);

        // Lock uploads before images, matching completion's lock order. An issued
        // upload URL remains usable even after verification or failure.
        var records = uploads.findDestinationForUpdate(location.provider(), location.container(), objectName);
        Instant now = Instant.now();
        if (records.stream().anyMatch(upload -> upload.getExpiresAt().isAfter(now))) {
            throw new FirmwareDeletionConflictException(
                    "An upload URL for this firmware is still valid. Wait for it to expire before deleting the file.");
        }
        var registeredImages = images.findDestinationForUpdate(location.provider(), location.container(), objectName);
        var imageIds = registeredImages.stream().map(FirmwareImage::getId).toList();
        if (!imageIds.isEmpty() && rsus.referencesFirmwareImages(imageIds)) {
            throw new FirmwareDeletionConflictException(
                    "This firmware is an RSU's current or target version and cannot be deleted.");
        }
        if (!imageIds.isEmpty() && failureHistory.existsByTargetFirmwareVersionIdIn(imageIds)) {
            throw new FirmwareDeletionConflictException(
                    "This firmware is referenced by upgrade failure history and cannot be deleted.");
        }

        // Flush database deletes first so foreign-key conflicts are detected before
        // touching storage. A storage failure rolls them back; an absent object
        // allows a retry to finish cleanup if a previous database commit failed.
        try {
            if (!imageIds.isEmpty()) {
                rules.deleteForImages(imageIds);
                images.deleteAll(registeredImages);
                images.flush();
            }
            uploads.deleteAll(records);
            uploads.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new FirmwareDeletionConflictException(
                    "This firmware is still referenced by other records and cannot be deleted.");
        }
        service.deleteObject(location, providerObjectVersion);
    }

    public static class FirmwareDeletionConflictException extends RuntimeException {
        public FirmwareDeletionConflictException(String message) {
            super(message);
        }
    }
}
