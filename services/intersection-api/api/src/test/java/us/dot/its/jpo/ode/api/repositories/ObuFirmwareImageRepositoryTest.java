package us.dot.its.jpo.ode.api.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuFirmwareDeviceType;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuFirmwareFileMetadata;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuFirmwareImage;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuFirmwareUpload;

@SpringBootTest(properties = "firmware-upload.cleanup.enabled=false")
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
@Transactional
class ObuFirmwareImageRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-09-03T20:00:00Z");

    @Autowired
    private ObuFirmwareImageRepository repository;

    @Autowired
    private ObuFirmwareUploadRepository uploads;

    @Autowired
    private ManufacturerRepository manufacturerRepository;

    @Autowired
    private EntityManager entityManager;

    private Manufacturer manufacturer;

    @BeforeEach
    void setUp() {
        manufacturer = new Manufacturer();
        manufacturer.setName("OBU Image Repository Vendor");
        manufacturer = manufacturerRepository.save(manufacturer);
    }

    @Test
    void storesCatalogMetadataAndFindsImagesByFileNameAndUpload() {
        var upload = saveUpload("ob4-generic-ro-secureboot-y20.48.2-b228647.tar.sig");
        var image = image("ob4-generic-ro-secureboot-y20.48.2-b228647.tar.sig", "gcp");
        image.setVerifiedUpload(upload);
        image.setPublished(true);
        image.setPublishedAt(NOW);
        image.setPublishedBy("admin");
        image = repository.saveAndFlush(image);
        entityManager.clear();

        var found = repository.findByManufacturerIdAndFileName(manufacturer.getId(), image.getFileName())
                .orElseThrow();
        assertThat(found.getMetadata()).isEqualTo(new ObuFirmwareFileMetadata(
                ObuFirmwareDeviceType.OBU, "y20.48.2-b228647", "ob4-generic", false, true));
        assertThat(found.getPublished()).isTrue();
        assertThat(found.isLocal()).isFalse();
        assertThat(repository.existsByManufacturerIdAndFileName(manufacturer.getId(), image.getFileName())).isTrue();
        assertThat(repository.findByVerifiedUploadId(upload.getId())).map(ObuFirmwareImage::getId)
                .contains(image.getId());
        assertThat(repository.findByIdForUpdate(image.getId())).isPresent();
        assertThat(repository.findDestinationForUpdate("gcp", "bucket", image.getObjectName())).hasSize(1);
    }

    @Test
    void recordsRegistrationTimeWithoutAnExplicitValue() {
        Instant before = Instant.now();

        var image = repository.saveAndFlush(image("registered.tar.sig", ObuFirmwareImage.LOCAL_STORAGE_PROVIDER));
        entityManager.clear();

        var registeredAt = repository.findById(image.getId()).orElseThrow().getRegisteredAt();
        assertThat(registeredAt).isNotNull().isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
    }

    @Test
    void listsImagesWithTheirManufacturer() {
        repository.saveAndFlush(image("ob4-generic-rw-y20.49.0-b230000.tar.sig", ObuFirmwareImage.LOCAL_STORAGE_PROVIDER));
        entityManager.clear();

        var images = repository.findAllWithManufacturer().stream()
                .filter(image -> image.getManufacturer().getId().equals(manufacturer.getId()))
                .toList();

        assertThat(images).hasSize(1);
        assertThat(images.get(0).getManufacturer().getName()).isEqualTo("OBU Image Repository Vendor");
        assertThat(images.get(0).isLocal()).isTrue();
        assertThat(images.get(0).getPublished()).isFalse();
    }

    @Test
    void rejectsTheSameFileNameTwiceForAManufacturer() {
        repository.saveAndFlush(image("dup.tar.sig", ObuFirmwareImage.LOCAL_STORAGE_PROVIDER));

        var duplicate = image("dup.tar.sig", ObuFirmwareImage.LOCAL_STORAGE_PROVIDER);
        duplicate.setObjectName("ota/elsewhere/dup.tar.sig");
        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("obu_firmware_images_manufacturer_file_name_unique");
    }

    private ObuFirmwareImage image(String fileName, String provider) {
        var image = new ObuFirmwareImage();
        image.setManufacturer(manufacturer);
        image.setFileName(fileName);
        image.setMetadata(metadata());
        image.setStorageProvider(provider);
        image.setStorageContainer(ObuFirmwareImage.LOCAL_STORAGE_PROVIDER.equals(provider) ? "local" : "bucket");
        image.setObjectName("ota/OBU Image Repository Vendor/y20.48.2-b228647/" + fileName);
        if (!ObuFirmwareImage.LOCAL_STORAGE_PROVIDER.equals(provider)) {
            image.setContentLength(1024L);
            image.setChecksumAlgorithm("CRC32C");
            image.setChecksum("ImIEBA==");
            image.setProviderObjectVersion("17");
        }
        return image;
    }

    private static ObuFirmwareFileMetadata metadata() {
        return new ObuFirmwareFileMetadata(ObuFirmwareDeviceType.OBU, "y20.48.2-b228647", "ob4-generic", false, true);
    }

    private ObuFirmwareUpload saveUpload(String fileName) {
        var upload = new ObuFirmwareUpload();
        upload.setId(UUID.randomUUID());
        upload.setManufacturer(manufacturer);
        upload.setFileName(fileName);
        upload.setMetadata(metadata());
        upload.setContentType("application/octet-stream");
        upload.setStorageProvider("gcp");
        upload.setStorageContainer("bucket");
        upload.setObjectName("ota/OBU Image Repository Vendor/y20.48.2-b228647/" + fileName);
        upload.setExpectedSize(1024L);
        upload.setChecksumAlgorithm("CRC32C");
        upload.setExpectedChecksum("ImIEBA==");
        upload.setStatus(FirmwareUploadStatus.VERIFIED);
        upload.setCreatedBy("admin");
        upload.setCreatedAt(NOW);
        upload.setExpiresAt(NOW);
        return uploads.saveAndFlush(upload);
    }
}
