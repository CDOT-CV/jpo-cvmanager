package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import us.dot.its.jpo.ode.api.models.postgres.tables.*;
import us.dot.its.jpo.ode.api.models.storage.*;
import us.dot.its.jpo.ode.api.repositories.*;
import us.dot.its.jpo.ode.api.storage.*;

class FirmwareObjectServiceTest {
    @Test
    void combinesObjectsWithEvidenceWithoutTrustingPathsOrReplacementObjects() {
        var registry = mock(ObjectStorageServiceRegistry.class);
        var storage = mock(ObjectStorageService.class);
        var uploads = mock(FirmwareUploadRepository.class);
        var images = mock(FirmwareImageRepository.class);
        when(registry.getActiveService()).thenReturn(storage);
        var checksum = new ObjectChecksum("CRC32C", "ImIEBA==");
        when(storage.listObjects(new ObjectListRequest("vendor/", true, 200, null)))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(
                        new StorageObject("vendor/model/v1/file", 9L, Instant.EPOCH, "1", checksum),
                        new StorageObject("vendor/replaced", 9L, Instant.EPOCH, "2", checksum)), "next"));
        when(storage.listObjects(new ObjectListRequest("vendor/", true, 200, "next")))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(
                        new StorageObject("vendor/unexpected path", 9L, Instant.EPOCH, "3", checksum)), null));
        FirmwareUpload valid = verified("vendor/model/v1/file");
        FirmwareUpload replaced = verified("vendor/replaced");
        when(uploads.findListingUploads(eq("gcp"), eq("bucket"), any())).thenReturn(List.of(valid, replaced));
        FirmwareImage image = new FirmwareImage();
        image.setId(7);
        image.setVerifiedUpload(valid);
        when(images.findByVerifiedUploadIdIn(any())).thenReturn(List.of(image));
        var result = new FirmwareObjectService(registry, uploads, images).list("vendor");
        assertThat(new String(Base64.getUrlDecoder().decode(result.objects().getFirst().objectId()),
                StandardCharsets.UTF_8)).isEqualTo("gcp\nvendor/model/v1/file");
        assertThat(result.objects()).extracting(FirmwareObjectPage.Item::verificationStatus)
                .containsExactly("VERIFIED", "CHANGED", "UNTRACKED");
        assertThat(result.objects().getFirst().firmwareId()).isEqualTo(7);
        assertThat(result.objects().get(2).uploadId()).isNull();
        verify(uploads).findListingUploads("gcp", "bucket",
                List.of("vendor/model/v1/file", "vendor/replaced", "vendor/unexpected path"));
        verify(storage).listObjects(new ObjectListRequest("vendor/", true, 200, null));
        verify(storage).listObjects(new ObjectListRequest("vendor/", true, 200, "next"));
    }

    @Test
    void emptyPageDoesNotQueryDatabase() {
        var registry = mock(ObjectStorageServiceRegistry.class);
        var storage = mock(ObjectStorageService.class);
        var uploads = mock(FirmwareUploadRepository.class);
        var images = mock(FirmwareImageRepository.class);
        when(registry.getActiveService()).thenReturn(storage);
        when(storage.providerName()).thenReturn("gcp");
        when(storage.listObjects(new ObjectListRequest(null, false, 200, null)))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(), null));
        var service = new FirmwareObjectService(registry, uploads, images);
        assertThat(service.list(null).objects()).isEmpty();
        verifyNoInteractions(uploads, images);
        assertThatThrownBy(() -> service.list("../vendor")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list("OTA")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("separate firmware workflow");
    }

    @Test
    void rootListingHidesReservedObuFirmwarePrefix() {
        var registry = mock(ObjectStorageServiceRegistry.class);
        var storage = mock(ObjectStorageService.class);
        var uploads = mock(FirmwareUploadRepository.class);
        var images = mock(FirmwareImageRepository.class);
        when(registry.getActiveService()).thenReturn(storage);
        when(storage.listObjects(new ObjectListRequest(null, false, 200, null)))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(
                        new StorageObject("ota/", 0, null, null, null),
                        new StorageObject("Commsignia/", 0, null, null, null)), null));
        when(uploads.findListingUploads("gcp", "bucket", List.of("Commsignia/")))
                .thenReturn(List.of());

        var result = new FirmwareObjectService(registry, uploads, images).list(null);

        assertThat(result.objects()).extracting(FirmwareObjectPage.Item::objectName)
                .containsExactly("Commsignia/");
        verify(uploads).findListingUploads("gcp", "bucket", List.of("Commsignia/"));
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
