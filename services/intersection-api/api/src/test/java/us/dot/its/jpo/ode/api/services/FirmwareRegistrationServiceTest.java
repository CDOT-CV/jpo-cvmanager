package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.postgres.tables.*;
import us.dot.its.jpo.ode.api.models.storage.*;
import us.dot.its.jpo.ode.api.repositories.*;
import us.dot.its.jpo.ode.api.services.FirmwareUploadService.FirmwareUploadVerificationException;
import us.dot.its.jpo.ode.api.services.FirmwareUploadService.FirmwareVersionAlreadyExistsException;

@SpringBootTest(properties = "firmware-upload.cleanup.enabled=false")
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
class FirmwareRegistrationServiceTest {
    @Autowired private FirmwareRegistrationService registration;
    @Autowired private FirmwareUploadRepository uploads;
    @MockitoSpyBean private FirmwareImageRepository images;
    @Autowired private ManufacturerRepository manufacturers;
    @Autowired private RsuModelRepository models;
    private Manufacturer manufacturer;
    private RsuModel model;
    private FirmwareUpload upload;
    private final StoredObjectMetadata metadata = new StoredObjectMetadata(
            9L, new ObjectChecksum("CRC32C", "ImIEBA=="), "123");

    @BeforeEach
    void setUp() {
        manufacturer = new Manufacturer();
        manufacturer.setName("Registration-" + UUID.randomUUID());
        manufacturer = manufacturers.save(manufacturer);
        model = new RsuModel();
        model.setName("Registration-" + UUID.randomUUID());
        model.setManufacturer(manufacturer);
        model.setSupportedRadio("C-V2X");
        model = models.save(model);
        upload = new FirmwareUpload();
        upload.setId(UUID.randomUUID());
        upload.setModel(model);
        upload.setVersion("v1");
        upload.setFileName("firmware.bin");
        upload.setContentType("application/octet-stream");
        upload.setStorageProvider("gcp");
        upload.setStorageContainer("test-bucket");
        upload.setObjectName(model.getName() + "/v1/firmware.bin");
        upload.setExpectedSize(9L);
        upload.setChecksumAlgorithm("CRC32C");
        upload.setExpectedChecksum("ImIEBA==");
        upload.setStatus(FirmwareUploadStatus.PENDING);
        upload.setCreatedBy("test");
        upload.setCreatedAt(Instant.now());
        upload.setExpiresAt(Instant.now().plusSeconds(900));
        uploads.saveAndFlush(upload);
    }

    @AfterEach
    void cleanUp() {
        reset(images);
        images.findByModelIdAndVersion(model.getId(), "v1").ifPresent(images::delete);
        uploads.deleteById(upload.getId());
        models.deleteById(model.getId());
        manufacturers.deleteById(manufacturer.getId());
    }

    @Test
    void registersExactlyOneImageOnRepeatedCompletion() {
        registration.register(upload.getId(), metadata);
        var first = images.findByModelIdAndVersion(model.getId(), "v1").orElseThrow();
        registration.register(upload.getId(), metadata);
        var second = images.findByModelIdAndVersion(model.getId(), "v1").orElseThrow();
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getName()).isEqualTo("v1");
        assertThat(second.getInstallPackage()).isEqualTo("firmware.bin");
        assertThat(second.getVerifiedUpload().getId()).isEqualTo(upload.getId());
        var verified = uploads.findById(upload.getId()).orElseThrow();
        assertThat(verified.getStatus()).isEqualTo(FirmwareUploadStatus.VERIFIED);
        assertThat(verified.getProviderObjectVersion()).isEqualTo("123");
    }

    @Test
    void rollsBackVerificationIfImagePersistenceFails() {
        doThrow(new DataIntegrityViolationException("image insert failed")).when(images).saveAndFlush(any());
        assertThatThrownBy(() -> registration.register(upload.getId(), metadata))
                .isInstanceOf(DataIntegrityViolationException.class);
        var pending = uploads.findById(upload.getId()).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(FirmwareUploadStatus.PENDING);
        assertThat(pending.getVerifiedAt()).isNull();
        assertThat(images.findByModelIdAndVersion(model.getId(), "v1")).isEmpty();
    }

    @Test
    void concurrentCompletionRegistersOnlyOneImage() throws Exception {
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<UUID> complete = () -> {
                start.await();
                return registration.register(upload.getId(), metadata).getId();
            };
            var first = executor.submit(complete);
            var second = executor.submit(complete);
            start.countDown();
            assertThat(first.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(upload.getId());
            assertThat(second.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(upload.getId());
            assertThat(images.findAll().stream().filter(image -> image.getModel().getId().equals(model.getId())))
                    .hasSize(1);
        }
    }

    @Test
    void permitsSameVersionAndFilenameForDifferentModelsButRejectsDuplicateModelVersion() {
        registration.register(upload.getId(), metadata);
        RsuModel otherModel = new RsuModel();
        otherModel.setName("Other-" + UUID.randomUUID());
        otherModel.setManufacturer(manufacturer);
        otherModel.setSupportedRadio("C-V2X");
        otherModel = models.save(otherModel);
        FirmwareImage other = new FirmwareImage();
        other.setModel(otherModel);
        other.setName("v1");
        other.setVersion("v1");
        other.setInstallPackage("firmware.bin");
        try {
            other = images.saveAndFlush(other);
            FirmwareImage duplicate = new FirmwareImage();
            duplicate.setModel(model);
            duplicate.setName("different-name");
            duplicate.setVersion("v1");
            duplicate.setInstallPackage("different-file.bin");
            assertThatThrownBy(() -> images.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            if (other.getId() != null) images.deleteById(other.getId());
            models.deleteById(otherModel.getId());
        }
    }

    @Test
    void mismatchedMetadataDoesNotRegisterImage() {
        assertThatThrownBy(() -> registration.register(upload.getId(),
                new StoredObjectMetadata(10L, metadata.checksum(), "123")))
                .isInstanceOf(FirmwareUploadVerificationException.class);
        assertThat(images.findByModelIdAndVersion(model.getId(), "v1")).isEmpty();
        assertThat(uploads.findById(upload.getId()).orElseThrow().getStatus()).isEqualTo(FirmwareUploadStatus.PENDING);
    }
}
