package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import us.dot.its.jpo.ode.api.models.storage.FirmwareObjectPage;
import us.dot.its.jpo.ode.api.models.storage.ObjectChecksum;
import us.dot.its.jpo.ode.api.models.storage.ObjectListRequest;
import us.dot.its.jpo.ode.api.models.storage.StorageObject;
import us.dot.its.jpo.ode.api.models.storage.StorageObjectPage;
import us.dot.its.jpo.ode.api.repositories.FirmwareImageRepository;
import us.dot.its.jpo.ode.api.repositories.FirmwareUploadRepository;
import us.dot.its.jpo.ode.api.storage.ObjectStorageService;
import us.dot.its.jpo.ode.api.storage.ObjectStorageServiceRegistry;

class FirmwareObjectServiceTest {
    @Test
    void returnsOneFirmwarePageWithParsedColumnsAndVerificationEvidence() {
        var registry = mock(ObjectStorageServiceRegistry.class);
        var storage = mock(ObjectStorageService.class);
        var uploads = mock(FirmwareUploadRepository.class);
        var images = mock(FirmwareImageRepository.class);
        when(registry.getActiveService()).thenReturn(storage);

        var checksum = new ObjectChecksum("CRC32C", "ImIEBA==");
        var request = new ObjectListRequest("Commsignia/", 25, "current");
        when(storage.listObjects(request)).thenReturn(new StorageObjectPage("gcp", "bucket", List.of(
                new StorageObject("Commsignia/ITS-RS4-M/v1/file.bin", 9L, Instant.EPOCH, "1", checksum),
                new StorageObject("Commsignia/ITS-RS4-M/v2/replaced.bin", 9L, Instant.EPOCH, "2", checksum),
                new StorageObject("Commsignia/ITS-RS4-M/v3/untracked.bin", 3L, Instant.EPOCH, "3", checksum)),
                "next"));

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
                .list(25, "current", "Commsignia");

        assertThat(result.nextPageToken()).isEqualTo("next");
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
        when(storage.listObjects(new ObjectListRequest(null, 100, null)))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(
                        new StorageObject("ota/obu.bin", 9L, null, null, null),
                        new StorageObject("Commsignia/", 0L, null, null, null)), "next"));

        var result = new FirmwareObjectService(registry, uploads, images).list(100, null, null);

        assertThat(result.objects()).isEmpty();
        assertThat(result.nextPageToken()).isEqualTo("next");
        verifyNoInteractions(uploads, images);
    }

    @Test
    void validatesPageSizeAndManufacturer() {
        var service = new FirmwareObjectService(
                mock(ObjectStorageServiceRegistry.class),
                mock(FirmwareUploadRepository.class),
                mock(FirmwareImageRepository.class));

        assertThatThrownBy(() -> service.list(0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list(100, null, "../vendor"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list(100, null, "OTA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("separate firmware workflow");
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
        return upload;
    }
}
