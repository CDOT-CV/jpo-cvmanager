package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareImage;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpload;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;
import us.dot.its.jpo.ode.api.models.storage.FirmwareObjectPage;
import us.dot.its.jpo.ode.api.models.storage.ObjectChecksum;
import us.dot.its.jpo.ode.api.models.storage.ObjectListRequest;
import us.dot.its.jpo.ode.api.models.storage.StorageObject;
import us.dot.its.jpo.ode.api.models.storage.StorageObjectPage;
import us.dot.its.jpo.ode.api.repositories.FirmwareImageRepository;
import us.dot.its.jpo.ode.api.repositories.FirmwareUploadRepository;
import us.dot.its.jpo.ode.api.storage.ObjectStorageService;
import us.dot.its.jpo.ode.api.storage.ObjectStorageServiceRegistry;
import us.dot.its.jpo.ode.api.storage.ObjectStorageUnavailableException;

class FirmwareObjectServiceTest {
    @Test
    void returnsOneFirmwarePageWithParsedColumnsAndVerificationEvidence() {
        var registry = mock(ObjectStorageServiceRegistry.class);
        var storage = mock(ObjectStorageService.class);
        var uploads = mock(FirmwareUploadRepository.class);
        var images = mock(FirmwareImageRepository.class);
        when(registry.getActiveService()).thenReturn(storage);

        var checksum = new ObjectChecksum("CRC32C", "ImIEBA==");
        var request = new ObjectListRequest("Commsignia/", 200, null);
        when(storage.listObjects(request)).thenReturn(new StorageObjectPage("gcp", "bucket", List.of(
                new StorageObject("Commsignia/ITS-RS4-M/v1/file.bin", 9L, Instant.EPOCH, "1", checksum),
                new StorageObject("Commsignia/ITS-RS4-M/v2/replaced.bin", 9L, Instant.EPOCH, "2", checksum),
                new StorageObject("Commsignia/ITS-RS4-M/v3/untracked.bin", 3L, Instant.EPOCH, "3", checksum)),
                null));

        FirmwareUpload valid = verified("Commsignia/ITS-RS4-M/v1/file.bin");
        FirmwareUpload replaced = verified("Commsignia/ITS-RS4-M/v2/replaced.bin");
        when(uploads.findListingUploads("gcp", "bucket", List.of(
                "Commsignia/ITS-RS4-M/v1/file.bin",
                "Commsignia/ITS-RS4-M/v2/replaced.bin",
                "Commsignia/ITS-RS4-M/v3/untracked.bin"))).thenReturn(List.of(valid, replaced));

        FirmwareImage image = new FirmwareImage();
        image.setId(7);
        image.setVerifiedUpload(valid);
        when(images.findByVerifiedUploadIdIn(List.of(valid.getId(), replaced.getId())))
                .thenReturn(List.of(image));

        FirmwareObjectPage result = new FirmwareObjectService(registry, uploads, images)
                .list(0, 25, "Commsignia", null, "manufacturer,asc");

        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(new String(Base64.getUrlDecoder().decode(result.objects().getFirst().objectId()),
                StandardCharsets.UTF_8)).isEqualTo("gcp\nCommsignia/ITS-RS4-M/v1/file.bin");
        assertThat(result.objects().getFirst())
                .returns("Commsignia", FirmwareObjectPage.Item::manufacturer)
                .returns("ITS-RS4-M", FirmwareObjectPage.Item::model)
                .returns("v1", FirmwareObjectPage.Item::version)
                .returns("file.bin", FirmwareObjectPage.Item::fileName)
                .returns(7, FirmwareObjectPage.Item::firmwareId);
        assertThat(result.objects()).extracting(FirmwareObjectPage.Item::verificationStatus)
                .containsExactly("VERIFIED", "CHANGED", "UNTRACKED");
        assertThat(result.objects().get(2).fileName()).isEqualTo("untracked.bin");
        verify(storage).listObjects(request);
    }

    @Test
    void excludesDirectoryMarkersAndReservedObuObjects() {
        var registry = mock(ObjectStorageServiceRegistry.class);
        var storage = mock(ObjectStorageService.class);
        var uploads = mock(FirmwareUploadRepository.class);
        var images = mock(FirmwareImageRepository.class);
        when(registry.getActiveService()).thenReturn(storage);
        when(storage.listObjects(new ObjectListRequest(null, 200, null)))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(
                        new StorageObject("ota/obu.bin", 9L, null, null, null),
                        new StorageObject("Commsignia/", 0L, null, null, null)), null));

        var result = new FirmwareObjectService(registry, uploads, images)
                .list(0, 100, null, null, "manufacturer,asc");

        assertThat(result.objects()).isEmpty();
        assertThat(result.totalElements()).isZero();
        verify(uploads, never()).findListingUploads(any(), any(), any());
        verify(images, never()).findByVerifiedUploadIdIn(any());
    }

    @Test
    void validatesPageSizeAndManufacturer() {
        var service = new FirmwareObjectService(
                mock(ObjectStorageServiceRegistry.class),
                mock(FirmwareUploadRepository.class),
                mock(FirmwareImageRepository.class));

        assertThatThrownBy(() -> service.list(-1, 25, null, null, "manufacturer,asc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list(0, 0, null, null, "manufacturer,asc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list(0, 201, null, null, "manufacturer,asc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list(0, 100, "../vendor", null, "manufacturer,asc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list(0, 100, "OTA", null, "manufacturer,asc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("separate firmware workflow");
    }

    @Test
    void searchesAllProviderPagesBeforePaginatingAndCountsOnlyMatchingFirmware() {
        var registry = mock(ObjectStorageServiceRegistry.class);
        var storage = mock(ObjectStorageService.class);
        var uploads = mock(FirmwareUploadRepository.class);
        var images = mock(FirmwareImageRepository.class);
        when(registry.getActiveService()).thenReturn(storage);
        when(storage.listObjects(new ObjectListRequest("Commsignia/", 200, null)))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(
                        object("Commsignia/model/old/old.tar.sig"),
                        object("Commsignia/model/release1/")), "second"));
        when(storage.listObjects(new ObjectListRequest("Commsignia/", 200, "second")))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(
                        object("Commsignia/model/release1/release1.tar.sig"),
                        object("Commsignia/model/release2/release2.tar.sig")), "third"));
        when(storage.listObjects(new ObjectListRequest("Commsignia/", 200, "third")))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(
                        object("Commsignia/model/release3/release3.tar.sig")), null));

        var service = new FirmwareObjectService(registry, uploads, images);
        var first = service.list(0, 1, "Commsignia", " RELEASE ", "manufacturer,asc");
        var second = service.list(1, 1, "Commsignia", "release", "manufacturer,asc");
        var last = service.list(2, 1, "Commsignia", "release", "manufacturer,asc");
        var beyondLast = service.list(3, 1, "Commsignia", "release", "manufacturer,asc");

        assertThat(first.objects()).extracting(FirmwareObjectPage.Item::version).containsExactly("release1");
        assertThat(second.objects()).extracting(FirmwareObjectPage.Item::version).containsExactly("release2");
        assertThat(last.objects()).extracting(FirmwareObjectPage.Item::version).containsExactly("release3");
        assertThat(List.of(first, second, last, beyondLast))
                .extracting(FirmwareObjectPage::totalElements).containsOnly(3L);
        assertThat(beyondLast.objects()).isEmpty();
        verify(uploads, times(4)).findListingUploads("gcp", "bucket", List.of(
                "Commsignia/model/release1/release1.tar.sig",
                "Commsignia/model/release2/release2.tar.sig",
                "Commsignia/model/release3/release3.tar.sig"));
        verify(images, never()).findByVerifiedUploadIdIn(any());
    }

    @Test
    void sortsTheCompleteFilteredResultBeforePaginating() {
        var registry = mock(ObjectStorageServiceRegistry.class);
        var storage = mock(ObjectStorageService.class);
        var uploads = mock(FirmwareUploadRepository.class);
        when(registry.getActiveService()).thenReturn(storage);
        when(storage.listObjects(new ObjectListRequest(null, 200, null)))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(
                        new StorageObject("Acme/model/v1/v1.bin", 10L, Instant.EPOCH, "1", null),
                        new StorageObject("Acme/model/v2/v2.bin", 30L, Instant.EPOCH, "2", null),
                        new StorageObject("Acme/model/v3/v3.bin", 20L, Instant.EPOCH, "3", null)), null));

        var result = new FirmwareObjectService(registry, uploads, mock(FirmwareImageRepository.class))
                .list(0, 2, null, null, "content_length,desc");

        assertThat(result.objects()).extracting(FirmwareObjectPage.Item::contentLength)
                .containsExactly(30L, 20L);
        assertThat(result.totalElements()).isEqualTo(3);
    }

    @Test
    void searchesManufacturerModelAndFilenameAndExcludesObuMatches() {
        var registry = mock(ObjectStorageServiceRegistry.class);
        var storage = mock(ObjectStorageService.class);
        var uploads = mock(FirmwareUploadRepository.class);
        var images = mock(FirmwareImageRepository.class);
        when(registry.getActiveService()).thenReturn(storage);
        when(storage.listObjects(new ObjectListRequest(null, 200, null)))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(
                        object("OTA/release.bin"), object("Commsignia/")), "second"));
        String name = "Commsignia/ITS-RS4-M/v1/update.tar.sig";
        when(storage.listObjects(new ObjectListRequest(null, 200, "second")))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(object(name)), null));
        var service = new FirmwareObjectService(registry, uploads, images);

        for (String search : List.of("commsignia", "its-rs4", "UPDATE.TAR", "")) {
            var result = service.list(0, 25, null, search, "manufacturer,asc");
            assertThat(result.objects()).extracting(FirmwareObjectPage.Item::objectName).containsExactly(name);
            assertThat(result.totalElements()).isOne();
        }
        var noMatches = service.list(0, 25, null, "release", "manufacturer,asc");
        assertThat(noMatches.objects()).isEmpty();
        assertThat(noMatches.totalElements()).isZero();
    }

    @Test
    void rejectsRepeatedProviderTokensInsteadOfLoopingIndefinitely() {
        var registry = mock(ObjectStorageServiceRegistry.class);
        var storage = mock(ObjectStorageService.class);
        when(registry.getActiveService()).thenReturn(storage);
        when(storage.listObjects(new ObjectListRequest(null, 200, null)))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(), "repeat"));
        when(storage.listObjects(new ObjectListRequest(null, 200, "repeat")))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(), "repeat"));
        var service = new FirmwareObjectService(registry,
                mock(FirmwareUploadRepository.class), mock(FirmwareImageRepository.class));

        assertThatThrownBy(() -> service.list(0, 25, null, "missing", "manufacturer,asc"))
                .isInstanceOf(ObjectStorageUnavailableException.class)
                .hasMessageContaining("pagination");
    }

    @Test
    void listsMissingTrackedAndLegacyFilesAfterAllStoragePagesAndAppliesFilters() {
        var registry = mock(ObjectStorageServiceRegistry.class);
        var storage = mock(ObjectStorageService.class);
        var uploads = mock(FirmwareUploadRepository.class);
        var images = mock(FirmwareImageRepository.class);
        when(registry.getActiveService()).thenReturn(storage);
        var missing = verified("Commsignia/model/v1/v1.tar");
        var present = verified("Commsignia/model/v2/v2.tar");
        var pending = verified("Commsignia/model/v3/v3.tar");
        pending.setStatus(FirmwareUploadStatus.PENDING);
        pending.setExpiresAt(Instant.now().plusSeconds(900));
        when(uploads.findListingCandidates("gcp", "bucket")).thenReturn(List.of(missing, present, pending));
        var manufacturer = new Manufacturer();
        manufacturer.setName("Commsignia");
        var model = new RsuModel();
        model.setManufacturer(manufacturer);
        model.setName("model");
        var legacy = new FirmwareImage();
        legacy.setId(42);
        legacy.setModel(model);
        legacy.setVersion("v0");
        legacy.setInstallPackage("legacy.tar");
        when(images.findLegacyImagesWithModelAndManufacturer()).thenReturn(List.of(legacy));
        when(storage.listObjects(new ObjectListRequest("Commsignia/", 200, null)))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(), "next"));
        when(storage.listObjects(new ObjectListRequest("Commsignia/", 200, "next")))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(object(present.getObjectName())), null));
        when(uploads.findListingUploads("gcp", "bucket", List.of(
                present.getObjectName(),
                legacy.getModel().getManufacturer().getName() + "/model/v0/legacy.tar",
                missing.getObjectName())))
                .thenReturn(List.of(missing, present));
        var service = new FirmwareObjectService(registry, uploads, images);

        var first = service.list(0, 1, "Commsignia", null, "manufacturer,asc");
        var second = service.list(1, 1, "Commsignia", null, "manufacturer,asc");
        var third = service.list(2, 1, "Commsignia", null, "manufacturer,asc");

        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.objects().getFirst()).returns("MISSING", FirmwareObjectPage.Item::verificationStatus)
                .returns(42, FirmwareObjectPage.Item::firmwareId).returns(null, FirmwareObjectPage.Item::contentLength);
        assertThat(second.objects().getFirst()).returns("MISSING", FirmwareObjectPage.Item::verificationStatus)
                .returns(missing.getId(), FirmwareObjectPage.Item::uploadId)
                .returns(null, FirmwareObjectPage.Item::providerObjectVersion);
        assertThat(third.objects().getFirst().objectName()).isEqualTo(present.getObjectName());
        assertThat(service.list(0, 25, "Commsignia", "legacy", "manufacturer,asc").totalElements()).isOne();

        when(storage.listObjects(new ObjectListRequest("Yunex/", 200, null)))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(), null));
        assertThat(service.list(0, 25, "Yunex", null, "manufacturer,asc").objects()).isEmpty();
    }

    @Test
    void doesNotReportMissingFilesWhenStorageListingFails() {
        var registry = mock(ObjectStorageServiceRegistry.class);
        var storage = mock(ObjectStorageService.class);
        var uploads = mock(FirmwareUploadRepository.class);
        when(registry.getActiveService()).thenReturn(storage);
        when(uploads.findListingCandidates("gcp", "bucket"))
                .thenReturn(List.of(verified("Commsignia/model/v1/v1.tar")));
        when(storage.listObjects(new ObjectListRequest(null, 200, null)))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(), "next"));
        when(storage.listObjects(new ObjectListRequest(null, 200, "next")))
                .thenThrow(new ObjectStorageUnavailableException("unavailable"));
        var service = new FirmwareObjectService(registry, uploads, mock(FirmwareImageRepository.class));

        assertThatThrownBy(() -> service.list(0, 25, null, null, "manufacturer,asc"))
                .isInstanceOf(ObjectStorageUnavailableException.class);
    }

    private StorageObject object(String name) {
        return new StorageObject(name, 9L, Instant.EPOCH, "1", null);
    }

    private FirmwareUpload verified(String name) {
        var upload = new FirmwareUpload();
        upload.setId(UUID.randomUUID());
        upload.setObjectName(name);
        upload.setStatus(FirmwareUploadStatus.VERIFIED);
        upload.setExpectedSize(9L);
        upload.setProviderObjectVersion("1");
        upload.setChecksumAlgorithm("CRC32C");
        upload.setObservedChecksum("ImIEBA==");
        upload.setExpiresAt(Instant.EPOCH);
        return upload;
    }
}
