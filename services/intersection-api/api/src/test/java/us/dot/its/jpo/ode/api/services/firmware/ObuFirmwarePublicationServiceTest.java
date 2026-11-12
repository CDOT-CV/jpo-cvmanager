package us.dot.its.jpo.ode.api.services.firmware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import jakarta.persistence.EntityNotFoundException;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuFirmwareDeviceType;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuFirmwareFileMetadata;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuFirmwareImage;
import us.dot.its.jpo.ode.api.models.storage.ObjectChecksum;
import us.dot.its.jpo.ode.api.models.storage.ObjectStorageLocation;
import us.dot.its.jpo.ode.api.models.storage.ObuFirmwareModels.ReplacedFirmware;
import us.dot.its.jpo.ode.api.models.storage.StoredObjectMetadata;
import us.dot.its.jpo.ode.api.repositories.ObuFirmwareImageRepository;
import us.dot.its.jpo.ode.api.storage.ObjectStorageService;
import us.dot.its.jpo.ode.api.storage.ObjectStorageServiceRegistry;

class ObuFirmwarePublicationServiceTest {
    private static final ObjectStorageLocation LOCATION =
            new ObjectStorageLocation("gcp", "bucket", "ota/Vendor/new.tar.sig");
    private static final ObjectChecksum CHECKSUM = new ObjectChecksum("CRC32C", "ImIEBA==");

    private final ObuFirmwareImageRepository images = mock(ObuFirmwareImageRepository.class);
    private final ObjectStorageServiceRegistry registry = mock(ObjectStorageServiceRegistry.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final ObuFirmwarePublicationService service = new ObuFirmwarePublicationService(images, registry);
    private final Manufacturer vendor = manufacturer(1);
    private final List<ObuFirmwareImage> vendorImages = new ArrayList<>();
    private ObuFirmwareImage image;

    @BeforeEach
    void setUp() {
        image = image(42, "new.tar.sig", vendor, false, true);
        vendorImages.add(image);
        when(images.findManufacturerIdById(42)).thenReturn(Optional.of(1));
        when(images.findManufacturerForUpdate(1)).thenReturn(vendorImages);
        when(images.findByIdForUpdate(42)).thenReturn(Optional.of(image));
        when(registry.getService("gcp")).thenReturn(storage);
        when(storage.getObjectMetadata(LOCATION, "CRC32C"))
                .thenReturn(Optional.of(new StoredObjectMetadata(1024L, CHECKSUM, "17")));
    }

    @Test
    void publishesAnUnchangedCloudFile() {
        var publication = service.setPublished(42, true, " admin@example.com ");

        assertThat(publication.published()).isTrue();
        assertThat(publication.publishedAt()).isNotNull();
        assertThat(publication.publishedBy()).isEqualTo("admin@example.com");
        assertThat(publication.replaced()).isEmpty();
        assertThat(image.getPublished()).isTrue();
        verify(images).saveAndFlush(image);
    }

    @Test
    void replacesThePublishedReleaseOfTheSameBuildOnly() {
        var sameBuild = published(image(10, "old.tar.sig", vendor, false, true));
        var otherFileSystem = published(image(11, "rw.tar.sig", vendor, true, true));
        var otherSecureBoot = published(image(12, "rw-plain.tar.sig", vendor, true, false));
        vendorImages.addAll(List.of(sameBuild, otherFileSystem, otherSecureBoot));

        var publication = service.setPublished(42, true, "admin");

        assertThat(publication.replaced()).containsExactly(new ReplacedFirmware(10, "old.tar.sig"));
        assertThat(sameBuild.getPublished()).isFalse();
        assertThat(sameBuild.getPublishedAt()).isNull();
        assertThat(sameBuild.getPublishedBy()).isNull();
        assertThat(otherFileSystem.getPublished()).isTrue();
        assertThat(otherSecureBoot.getPublished()).isTrue();
        assertThat(image.getPublished()).isTrue();
        // The current release is withdrawn before the new one is published
        var order = inOrder(images);
        order.verify(images).saveAllAndFlush(List.of(sameBuild));
        order.verify(images).saveAndFlush(image);
    }

    @Test
    void comparesBuildsWithinOneManufacturerAndDeviceType() {
        var otherVendor = published(image(20, "other.tar.sig", manufacturer(2), false, true));
        var rsuBuild = published(image(21, "rsu.tar.sig", vendor, false, true));
        rsuBuild.getMetadata().setDeviceType(ObuFirmwareDeviceType.RSU);
        var otherVariant = published(image(22, "variant.tar.sig", vendor, false, true));
        otherVariant.getMetadata().setVariant("ob4-generic-custom");

        assertThat(ObuFirmwarePublicationService.sameBuild(image, otherVendor)).isFalse();
        assertThat(ObuFirmwarePublicationService.sameBuild(image, rsuBuild)).isFalse();
        assertThat(ObuFirmwarePublicationService.sameBuild(image, otherVariant)).isFalse();
        assertThat(ObuFirmwarePublicationService.sameBuild(image, image(23, "x.tar.sig", vendor, false, true))).isTrue();
    }

    @Test
    void refusesToPublishAMissingOrChangedFile() {
        var current = published(image(10, "old.tar.sig", vendor, false, true));
        vendorImages.add(current);
        for (var stored : new StoredObjectMetadata[] { null, new StoredObjectMetadata(1024L, CHECKSUM, "18"),
                new StoredObjectMetadata(2048L, CHECKSUM, "17"),
                new StoredObjectMetadata(1024L, new ObjectChecksum("CRC32C", "other=="), "17") }) {
            when(storage.getObjectMetadata(LOCATION, "CRC32C")).thenReturn(Optional.ofNullable(stored));

            assertThatThrownBy(() -> service.setPublished(42, true, "admin"))
                    .isInstanceOf(ObuFirmwareConflictException.class)
                    .hasMessageContaining("missing or has changed");
        }
        assertThat(image.getPublished()).isFalse();
        assertThat(current.getPublished()).isTrue();
        verify(images, never()).saveAndFlush(any());
        verify(images, never()).saveAllAndFlush(anyList());
    }

    @Test
    void reportsAConcurrentPublicationOfTheSameBuildAsAConflict() {
        when(images.saveAndFlush(image)).thenThrow(new DataIntegrityViolationException(
                "violates uq_obu_firmware_images_published_build"));

        assertThatThrownBy(() -> service.setPublished(42, true, "admin"))
                .isInstanceOf(ObuFirmwareConflictException.class)
                .hasMessageContaining("published at the same time");
    }

    @Test
    void publishesLocalFilesWithoutReadingStorage() {
        image.setStorageProvider(ObuFirmwareImage.LOCAL_STORAGE_PROVIDER);

        assertThat(service.setPublished(42, true, "admin").published()).isTrue();
        verifyNoInteractions(registry);
    }

    @Test
    void withdrawsFirmwareAndClearsWhoPublishedIt() {
        published(image);

        var publication = service.setPublished(42, false, "other");

        assertThat(publication.published()).isFalse();
        assertThat(publication.publishedAt()).isNull();
        assertThat(publication.publishedBy()).isNull();
        assertThat(publication.replaced()).isEmpty();
        verifyNoInteractions(registry);
        verify(images).save(image);
    }

    @Test
    void repeatingTheCurrentStateChangesNothing() {
        assertThat(service.setPublished(42, false, "admin").published()).isFalse();
        verify(images, never()).save(any());

        published(image);
        var publishedAt = image.getPublishedAt();
        var publication = service.setPublished(42, true, "someone-else");
        assertThat(publication.publishedAt()).isEqualTo(publishedAt);
        assertThat(publication.replaced()).isEmpty();
        verify(images, never()).saveAndFlush(any());
        verifyNoInteractions(registry);
    }

    @Test
    void rejectsUnknownFirmware() {
        when(images.findManufacturerIdById(7)).thenReturn(Optional.empty());
        when(images.findByIdForUpdate(7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPublished(7, true, "admin"))
                .isInstanceOf(EntityNotFoundException.class).hasMessageContaining("7");
        assertThatThrownBy(() -> service.setPublished(7, false, "admin"))
                .isInstanceOf(EntityNotFoundException.class).hasMessageContaining("7");
    }

    @Test
    void rejectsFirmwareDeletedBeforeTheLock() {
        when(images.findManufacturerIdById(8)).thenReturn(Optional.of(1));

        assertThatThrownBy(() -> service.setPublished(8, true, "admin"))
                .isInstanceOf(EntityNotFoundException.class).hasMessageContaining("8");
    }

    private static ObuFirmwareImage image(int id, String fileName, Manufacturer manufacturer, boolean writable,
            boolean secureBoot) {
        var image = new ObuFirmwareImage();
        image.setId(id);
        image.setManufacturer(manufacturer);
        image.setFileName(fileName);
        image.setMetadata(new ObuFirmwareFileMetadata(ObuFirmwareDeviceType.OBU, "y20.48.2", "ob4-generic",
                writable, secureBoot));
        image.setStorageProvider("gcp");
        image.setStorageContainer("bucket");
        image.setObjectName("ota/Vendor/" + fileName);
        image.setContentLength(1024L);
        image.setChecksumAlgorithm("CRC32C");
        image.setChecksum(CHECKSUM.value());
        image.setProviderObjectVersion("17");
        return image;
    }

    private static ObuFirmwareImage published(ObuFirmwareImage image) {
        image.setPublished(true);
        image.setPublishedAt(Instant.parse("2026-10-15T12:00:00Z"));
        image.setPublishedBy("admin");
        return image;
    }

    private static Manufacturer manufacturer(int id) {
        var manufacturer = new Manufacturer();
        manufacturer.setId(id);
        manufacturer.setName("Vendor " + id);
        return manufacturer;
    }
}
