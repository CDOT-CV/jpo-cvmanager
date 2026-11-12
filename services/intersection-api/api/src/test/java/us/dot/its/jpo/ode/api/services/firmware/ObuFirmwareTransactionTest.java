package us.dot.its.jpo.ode.api.services.firmware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuFirmwareImage;
import us.dot.its.jpo.ode.api.models.storage.ObjectChecksum;
import us.dot.its.jpo.ode.api.models.storage.ObjectStorageLocation;
import us.dot.its.jpo.ode.api.models.storage.StoredObjectMetadata;
import us.dot.its.jpo.ode.api.repositories.ManufacturerRepository;
import us.dot.its.jpo.ode.api.repositories.ObuFirmwareImageRepository;
import us.dot.its.jpo.ode.api.storage.ObjectIds;
import us.dot.its.jpo.ode.api.storage.ObjectStorageService;
import us.dot.its.jpo.ode.api.storage.ObjectStorageServiceRegistry;
import us.dot.its.jpo.ode.api.storage.ObjectStorageUnavailableException;

/** Publication and deletion against real PostgreSQL locks and transactions. */
@SpringBootTest(properties = "firmware-upload.cleanup.enabled=false")
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
class ObuFirmwareTransactionTest {
    private static final String FILE_NAME = "ob4-generic-ro-secureboot-y20.48.2-b228647.tar.sig";
    private static final String NEXT_RELEASE = "ob4-generic-ro-secureboot-y20.49.0-b230001.tar.sig";
    private static final ObjectChecksum CHECKSUM = new ObjectChecksum("CRC32C", "ImIEBA==");

    @Autowired private ObuFirmwarePublicationService publication;
    @Autowired private ObuFirmwareDeletionService deletion;
    @Autowired private ObuFirmwareImageRepository images;
    @Autowired private ManufacturerRepository manufacturers;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private ObjectStorageServiceRegistry storage;

    private final ObjectStorageService provider = mock(ObjectStorageService.class);
    private Manufacturer manufacturer;
    private ObuFirmwareImage image;
    private ObjectStorageLocation location;
    private final List<ObuFirmwareImage> extraImages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        manufacturer = new Manufacturer();
        manufacturer.setName("ObuTransaction-" + UUID.randomUUID());
        manufacturer = manufacturers.save(manufacturer);

        when(storage.getActiveService()).thenReturn(provider);
        when(storage.getService("gcp")).thenReturn(provider);
        when(provider.providerName()).thenReturn("gcp");
        image = saveImage(FILE_NAME);
        location = new ObjectStorageLocation("gcp", "bucket", image.getObjectName());
    }

    @AfterEach
    void cleanUp() {
        images.findById(image.getId()).ifPresent(images::delete);
        extraImages.forEach(extra -> images.findById(extra.getId()).ifPresent(images::delete));
        manufacturers.deleteById(manufacturer.getId());
    }

    @Test
    void deletionWaitsForAConcurrentPublishAndThenRefuses() throws Exception {
        var published = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            // Publish and hold the transaction open, so the row lock outlives the call
            var publishing = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                publication.setPublished(image.getId(), true, "admin");
                published.countDown();
                await(commit);
            }));
            assertThat(published.await(20, TimeUnit.SECONDS)).isTrue();

            var deleting = executor.submit(() -> deletion.delete(ObjectIds.encode("gcp", image.getObjectName()), "17"));
            Thread.sleep(500);
            assertThat(deleting.isDone()).as("deletion waits for the publication's row lock").isFalse();

            commit.countDown();
            publishing.get(20, TimeUnit.SECONDS);
            assertThatThrownBy(() -> deleting.get(20, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(ObuFirmwareConflictException.class)
                    .hasMessageContaining(ObuFirmwareDeletionService.UNPUBLISH_FIRST_MESSAGE);
        }
        verify(provider, never()).deleteObject(any(), any());
        assertThat(images.findById(image.getId())).get().returns(true, ObuFirmwareImage::getPublished);
    }

    @Test
    void aStorageFailureKeepsTheCatalogEntry() {
        doThrow(new ObjectStorageUnavailableException("retry")).when(provider).deleteObject(location, "17");

        assertThatThrownBy(() -> deletion.delete(ObjectIds.encode("gcp", image.getObjectName()), "17"))
                .isInstanceOf(ObjectStorageUnavailableException.class);

        assertThat(images.findById(image.getId())).isPresent();
    }

    @Test
    void concurrentPublicationsOfOneBuildLeaveExactlyOnePublished() throws Exception {
        var next = saveImage(NEXT_RELEASE);
        extraImages.add(next);
        createPublishedBuildIndex();
        try {
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> {
                    await(start);
                    return publication.setPublished(image.getId(), true, "first");
                });
                var second = executor.submit(() -> {
                    await(start);
                    return publication.setPublished(next.getId(), true, "second");
                });
                start.countDown();
                var results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));

                // The manufacturer lock serializes them, so the later one replaces the earlier
                assertThat(results).filteredOn(result -> !result.replaced().isEmpty()).hasSize(1);
            }
            var published = images.findAll().stream()
                    .filter(candidate -> candidate.getManufacturer().getId().equals(manufacturer.getId()))
                    .filter(candidate -> Boolean.TRUE.equals(candidate.getPublished()))
                    .toList();
            assertThat(published).hasSize(1);
        } finally {
            dropPublishedBuildIndex();
        }
    }

    @Test
    void theDatabaseAllowsOnePublishedReleasePerBuild() {
        var next = saveImage(NEXT_RELEASE);
        extraImages.add(next);
        createPublishedBuildIndex();
        try {
            publication.setPublished(image.getId(), true, "admin");
            next.setPublished(true);

            assertThatThrownBy(() -> images.saveAndFlush(next)).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            dropPublishedBuildIndex();
        }
    }

    private ObuFirmwareImage saveImage(String fileName) {
        var saved = new ObuFirmwareImage();
        saved.setManufacturer(manufacturer);
        saved.setFileName(fileName);
        saved.setMetadata(new CommsigniaObuFirmwareFileNameParser().parse(fileName).orElseThrow());
        saved.setStorageProvider("gcp");
        saved.setStorageContainer("bucket");
        saved.setObjectName("ota/" + manufacturer.getName() + "/" + fileName);
        saved.setContentLength(1024L);
        saved.setChecksumAlgorithm(CHECKSUM.algorithm());
        saved.setChecksum(CHECKSUM.value());
        saved.setProviderObjectVersion("17");
        saved = images.saveAndFlush(saved);

        var savedLocation = new ObjectStorageLocation("gcp", "bucket", saved.getObjectName());
        when(provider.getLocation(saved.getObjectName())).thenReturn(savedLocation);
        when(provider.getObjectMetadata(savedLocation, "CRC32C"))
                .thenReturn(Optional.of(new StoredObjectMetadata(1024L, CHECKSUM, "17")));
        return saved;
    }

    /** Integration tests build the schema from entities, so add the migration's index here. */
    private void createPublishedBuildIndex() {
        jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_obu_firmware_images_published_build
                    ON obu_firmware_images (manufacturer, device_type, variant, writable_system_partition, secure_boot)
                    NULLS NOT DISTINCT
                    WHERE published
                """);
    }

    private void dropPublishedBuildIndex() {
        jdbc.execute("DROP INDEX IF EXISTS uq_obu_firmware_images_published_build");
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
