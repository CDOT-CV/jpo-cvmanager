package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityNotFoundException;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpload;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadUrlRequest;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadVerification;
import us.dot.its.jpo.ode.api.models.storage.ObjectChecksum;
import us.dot.its.jpo.ode.api.models.storage.ObjectStorageLocation;
import us.dot.its.jpo.ode.api.models.storage.ObjectUploadRequest;
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.StoredObjectMetadata;
import us.dot.its.jpo.ode.api.repositories.FirmwareUploadRepository;
import us.dot.its.jpo.ode.api.repositories.RsuModelRepository;
import us.dot.its.jpo.ode.api.storage.ObjectStorageProperties;
import us.dot.its.jpo.ode.api.storage.ObjectStorageService;
import us.dot.its.jpo.ode.api.storage.ObjectStorageServiceRegistry;

class FirmwareUploadServiceTest {
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-02T12:15:00Z");

    private final RsuModelRepository rsuModelRepository = mock(RsuModelRepository.class);
    private final FirmwareUploadRepository firmwareUploadRepository = mock(FirmwareUploadRepository.class);
    private final ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
    private final ObjectStorageServiceRegistry objectStorageServices = mock(ObjectStorageServiceRegistry.class);
    private final ObjectStorageProperties properties = new ObjectStorageProperties();

    private FirmwareUploadService service;
    private FirmwareUploadUrlRequest request;
    private RsuModel model;

    @BeforeEach
    void setUp() {
        properties.setMaxUploadSize(DataSize.ofMegabytes(100));
        service = new FirmwareUploadService(rsuModelRepository, firmwareUploadRepository,
                objectStorageServices, properties);

        request = new FirmwareUploadUrlRequest();
        request.setVendorName("Commsignia");
        request.setModelName("ITS-RS4-M");
        request.setVersion("y20.97.0");
        request.setFileName("rs4-generic-ro-secureboot-y20.97.0-b377993.tar.sig");
        request.setContentLength(50L * 1024 * 1024);
        request.setChecksumAlgorithm("CRC32C");
        request.setChecksum("ImIEBA==");
        request.setContentType("application/octet-stream");

        model = new RsuModel();
        model.setId(7);
        when(rsuModelRepository.findByNameAndManufacturerName("ITS-RS4-M", "Commsignia"))
                .thenReturn(Optional.of(model));
        when(objectStorageService.providerName()).thenReturn("gcp");
        when(objectStorageServices.getActiveService()).thenReturn(objectStorageService);
        when(objectStorageServices.getService("gcp")).thenReturn(objectStorageService);
    }

    @Test
    void createsPendingUploadIntentAndReturnsItsId() {
        SignedUploadUrl signedUrl = new SignedUploadUrl("https://storage.googleapis.com/signed", "PUT",
                new ObjectStorageLocation("gcp", "firmware-bucket",
                        "Commsignia/ITS-RS4-M/y20.97.0/rs4-generic-ro-secureboot-y20.97.0-b377993.tar.sig"),
                EXPIRES_AT, Map.of("x-goog-hash", "crc32c=ImIEBA=="));
        when(objectStorageService.createSignedUploadUrl(any(ObjectUploadRequest.class))).thenReturn(signedUrl);

        FirmwareUploadUrl result = service.createFirmwareSignedUploadUrl(request, "admin@example.com");

        ArgumentCaptor<ObjectUploadRequest> requestCaptor = ArgumentCaptor.forClass(ObjectUploadRequest.class);
        verify(objectStorageService).createSignedUploadUrl(requestCaptor.capture());
        assertThat(requestCaptor.getValue().objectName()).isEqualTo(
                "Commsignia/ITS-RS4-M/y20.97.0/rs4-generic-ro-secureboot-y20.97.0-b377993.tar.sig");
        assertThat(requestCaptor.getValue().checksum()).isEqualTo(new ObjectChecksum("CRC32C", "ImIEBA=="));

        ArgumentCaptor<FirmwareUpload> uploadCaptor = ArgumentCaptor.forClass(FirmwareUpload.class);
        verify(firmwareUploadRepository).save(uploadCaptor.capture());
        FirmwareUpload upload = uploadCaptor.getValue();
        assertThat(upload.getId()).isEqualTo(result.uploadId());
        assertThat(upload.getModel()).isSameAs(model);
        assertThat(upload.getExpectedSize()).isEqualTo(request.getContentLength());
        assertThat(upload.getChecksumAlgorithm()).isEqualTo("CRC32C");
        assertThat(upload.getExpectedChecksum()).isEqualTo("ImIEBA==");
        assertThat(upload.getStorageProvider()).isEqualTo("gcp");
        assertThat(upload.getStorageContainer()).isEqualTo("firmware-bucket");
        assertThat(upload.getCreatedBy()).isEqualTo("admin@example.com");
        assertThat(upload.getStatus()).isEqualTo(FirmwareUploadStatus.PENDING);
        assertThat(upload.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(result.uploadUrl()).isEqualTo(signedUrl.uploadUrl());
        verify(objectStorageService).objectExists(
                "Commsignia/ITS-RS4-M/y20.97.0/rs4-generic-ro-secureboot-y20.97.0-b377993.tar.sig");
    }

    @Test
    void rejectsExistingObjectBeforeSigningOrSavingIntent() {
        when(objectStorageService.objectExists(any())).thenReturn(true);

        assertThatThrownBy(() -> service.createFirmwareSignedUploadUrl(request, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).contains("already exists");
                });

        verify(objectStorageService, never()).createSignedUploadUrl(any());
        verify(firmwareUploadRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownVendorModelPair() {
        when(rsuModelRepository.findByNameAndManufacturerName("ITS-RS4-M", "Unknown"))
                .thenReturn(Optional.empty());
        request.setVendorName("Unknown");

        assertThatThrownBy(() -> service.createFirmwareSignedUploadUrl(request, "admin"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("ITS-RS4-M")
                .hasMessageContaining("Unknown");
        verify(objectStorageService, never()).createSignedUploadUrl(any());
    }

    @Test
    void rejectsUploadsLargerThanConfiguredMaximum() {
        request.setContentLength(DataSize.ofMegabytes(100).toBytes() + 1);

        assertThatThrownBy(() -> service.createFirmwareSignedUploadUrl(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content_length")
                .hasMessageContaining(String.valueOf(DataSize.ofMegabytes(100).toBytes()));
        verify(objectStorageService, never()).createSignedUploadUrl(any());
    }

    @Test
    void verifiesUploadedObjectMetadata() {
        FirmwareUpload upload = pendingUpload();
        when(firmwareUploadRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(objectStorageService.getObjectMetadata(any(ObjectStorageLocation.class), eq("CRC32C")))
                .thenReturn(Optional.of(new StoredObjectMetadata(
                        12345L, new ObjectChecksum("CRC32C", "ImIEBA=="), "17")));

        FirmwareUploadVerification result = service.completeFirmwareUpload(upload.getId());

        assertThat(result.status()).isEqualTo(FirmwareUploadStatus.VERIFIED);
        assertThat(result.checksumAlgorithm()).isEqualTo("CRC32C");
        assertThat(result.checksum()).isEqualTo("ImIEBA==");
        assertThat(result.providerObjectVersion()).isEqualTo("17");
        assertThat(result.verifiedAt()).isNotNull();
        assertThat(upload.getStatus()).isEqualTo(FirmwareUploadStatus.VERIFIED);
        assertThat(upload.getFinishedAt()).isEqualTo(upload.getVerifiedAt());
        assertThat(upload.getFailureReason()).isNull();
        verify(firmwareUploadRepository).save(upload);
    }

    @Test
    void rejectsCompletionWhenObjectIsMissing() {
        FirmwareUpload upload = pendingUpload();
        when(firmwareUploadRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(objectStorageService.getObjectMetadata(any(ObjectStorageLocation.class), eq("CRC32C")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeFirmwareUpload(upload.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(firmwareUploadRepository, never()).save(upload);
    }

    @Test
    void rejectsCompletionWhenChecksumDoesNotMatch() {
        FirmwareUpload upload = pendingUpload();
        when(firmwareUploadRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(objectStorageService.getObjectMetadata(any(ObjectStorageLocation.class), eq("CRC32C")))
                .thenReturn(Optional.of(new StoredObjectMetadata(
                        12345L, new ObjectChecksum("CRC32C", "AAAAAA=="), "17")));

        assertThatThrownBy(() -> service.completeFirmwareUpload(upload.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).contains("checksum");
                });
        assertThat(upload.getStatus()).isEqualTo(FirmwareUploadStatus.FAILED);
        assertThat(upload.getFailureReason()).isEqualTo("CHECKSUM_MISMATCH");
        assertThat(upload.getFinishedAt()).isNotNull();
        verify(firmwareUploadRepository).save(upload);
    }

    @Test
    void verifiedCompletionIsIdempotent() {
        FirmwareUpload upload = pendingUpload();
        upload.setStatus(FirmwareUploadStatus.VERIFIED);
        upload.setObservedChecksum("ImIEBA==");
        upload.setProviderObjectVersion("17");
        upload.setVerifiedAt(Instant.parse("2026-09-02T12:10:00Z"));
        when(firmwareUploadRepository.findById(upload.getId())).thenReturn(Optional.of(upload));

        FirmwareUploadVerification result = service.completeFirmwareUpload(upload.getId());

        assertThat(result.status()).isEqualTo(FirmwareUploadStatus.VERIFIED);
        verify(objectStorageService, never()).getObjectMetadata(any(), any());
    }

    @Test
    void validLateCompletionRecoversExpiredUpload() {
        FirmwareUpload upload = pendingUpload();
        upload.setStatus(FirmwareUploadStatus.EXPIRED);
        upload.setFailureReason("SIGNED_URL_EXPIRED");
        upload.setFinishedAt(Instant.parse("2026-09-02T13:15:00Z"));
        when(firmwareUploadRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(objectStorageService.getObjectMetadata(any(ObjectStorageLocation.class), eq("CRC32C")))
                .thenReturn(Optional.of(new StoredObjectMetadata(
                        12345L, new ObjectChecksum("CRC32C", "ImIEBA=="), "17")));

        FirmwareUploadVerification result = service.completeFirmwareUpload(upload.getId());

        assertThat(result.status()).isEqualTo(FirmwareUploadStatus.VERIFIED);
        assertThat(upload.getFailureReason()).isNull();
        assertThat(upload.getFinishedAt()).isEqualTo(upload.getVerifiedAt());
        verify(firmwareUploadRepository).save(upload);
    }

    private FirmwareUpload pendingUpload() {
        FirmwareUpload upload = new FirmwareUpload();
        upload.setId(UUID.fromString("1ef8f6f7-cae8-45cc-af92-8de58f5ffed8"));
        upload.setModel(model);
        upload.setObjectName("Commsignia/ITS-RS4-M/y20.97.0/firmware.bin");
        upload.setStorageProvider("gcp");
        upload.setStorageContainer("firmware-bucket");
        upload.setExpectedSize(12345L);
        upload.setChecksumAlgorithm("CRC32C");
        upload.setExpectedChecksum("ImIEBA==");
        upload.setStatus(FirmwareUploadStatus.PENDING);
        return upload;
    }
}
