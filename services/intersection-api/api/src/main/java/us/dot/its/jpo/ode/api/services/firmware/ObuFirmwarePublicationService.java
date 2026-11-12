package us.dot.its.jpo.ode.api.services.firmware;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import us.dot.its.jpo.ode.api.models.postgres.tables.ObuFirmwareImage;
import us.dot.its.jpo.ode.api.models.storage.ObjectStorageLocation;
import us.dot.its.jpo.ode.api.models.storage.ObuFirmwareModels.Publication;
import us.dot.its.jpo.ode.api.models.storage.ObuFirmwareModels.ReplacedFirmware;
import us.dot.its.jpo.ode.api.storage.ObjectStorageServiceRegistry;
import us.dot.its.jpo.ode.api.repositories.ObuFirmwareImageRepository;

/**
 * Publishes OBU firmware to, or withdraws it from, what the OBU OTA server offers.
 * An OBU installs the newest published firmware matching its own build, so only
 * one release per build is published: publishing a release withdraws the release
 * of the same build that was published before, whichever version it is.
 */
@Service
@RequiredArgsConstructor
public class ObuFirmwarePublicationService {
    private final ObuFirmwareImageRepository images;
    private final ObjectStorageServiceRegistry storage;

    /**
     * Sets the publish state. Both directions are idempotent. Publishing a cloud
     * file first checks that storage still holds the exact version that was
     * verified, so OBUs are never offered a replaced or missing file.
     */
    @Transactional
    public Publication setPublished(Integer obuFirmwareId, boolean published, String publishedBy) {
        return published ? publish(obuFirmwareId, publishedBy) : unpublish(obuFirmwareId);
    }

    private Publication publish(Integer obuFirmwareId, String publishedBy) {
        // Lock every image of the manufacturer in id order, so concurrent publications
        // of the same build serialize and see each other's result. Only the id is read
        // first, so the images themselves are loaded under the lock.
        Integer manufacturerId = images.findManufacturerIdById(obuFirmwareId).orElseThrow(() -> notFound(obuFirmwareId));
        List<ObuFirmwareImage> manufacturerImages = images.findManufacturerForUpdate(manufacturerId);
        ObuFirmwareImage image = manufacturerImages.stream()
                .filter(candidate -> candidate.getId().equals(obuFirmwareId))
                .findFirst()
                .orElseThrow(() -> notFound(obuFirmwareId));
        if (Boolean.TRUE.equals(image.getPublished())) {
            return publication(image, List.of());
        }
        requireVerifiedFile(image);

        List<ObuFirmwareImage> replaced = manufacturerImages.stream()
                .filter(candidate -> Boolean.TRUE.equals(candidate.getPublished()))
                .filter(candidate -> sameBuild(candidate, image))
                .toList();
        try {
            // Withdraw the current release before publishing, so the one-published-
            // release-per-build index never sees two at once
            replaced.forEach(this::withdraw);
            images.saveAllAndFlush(replaced);
            image.setPublished(true);
            image.setPublishedAt(Instant.now());
            image.setPublishedBy(FirmwareUploadSupport.normalizeCreatedBy(publishedBy));
            images.saveAndFlush(image);
        } catch (DataIntegrityViolationException ex) {
            throw new ObuFirmwareConflictException(
                    "Another release of this build was published at the same time. Refresh and try again.");
        }
        return publication(image, replaced.stream()
                .map(withdrawn -> new ReplacedFirmware(withdrawn.getId(), withdrawn.getFileName()))
                .toList());
    }

    private Publication unpublish(Integer obuFirmwareId) {
        // Lock the entry so publication and deletion of the same firmware serialize
        ObuFirmwareImage image = images.findByIdForUpdate(obuFirmwareId).orElseThrow(() -> notFound(obuFirmwareId));
        if (Boolean.TRUE.equals(image.getPublished())) {
            withdraw(image);
            images.save(image);
        }
        return publication(image, List.of());
    }

    /** A build is the device and file system configuration an OBU installs firmware for. */
    static boolean sameBuild(ObuFirmwareImage first, ObuFirmwareImage second) {
        var a = first.getMetadata();
        var b = second.getMetadata();
        return Objects.equals(first.getManufacturer().getId(), second.getManufacturer().getId())
                && a.getDeviceType() == b.getDeviceType()
                && Objects.equals(a.getVariant(), b.getVariant())
                && Objects.equals(a.getWritableSystemPartition(), b.getWritableSystemPartition())
                && Objects.equals(a.getSecureBoot(), b.getSecureBoot());
    }

    private void withdraw(ObuFirmwareImage image) {
        image.setPublished(false);
        image.setPublishedAt(null);
        image.setPublishedBy(null);
    }

    private static Publication publication(ObuFirmwareImage image, List<ReplacedFirmware> replaced) {
        return new Publication(image.getId(), image.getPublished(), image.getPublishedAt(), image.getPublishedBy(),
                replaced);
    }

    private static EntityNotFoundException notFound(Integer obuFirmwareId) {
        return new EntityNotFoundException("OBU firmware '" + obuFirmwareId + "' was not found");
    }

    private void requireVerifiedFile(ObuFirmwareImage image) {
        // The API cannot inspect the OBU OTA server's local firmware folder
        if (image.isLocal()) {
            return;
        }
        var location = new ObjectStorageLocation(
                image.getStorageProvider(), image.getStorageContainer(), image.getObjectName());
        boolean verified = storage.getService(image.getStorageProvider())
                .getObjectMetadata(location, image.getChecksumAlgorithm())
                .filter(metadata -> ObuFirmwareObjectService.matchesStoredFile(image,
                        metadata.providerObjectVersion(), metadata.contentLength(), metadata.checksum()))
                .isPresent();
        if (!verified) {
            throw new ObuFirmwareConflictException("The firmware file is missing or has changed in storage. "
                    + "Refresh the OBU firmware list before publishing it.");
        }
    }
}
