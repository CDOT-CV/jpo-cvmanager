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
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.unit.DataSize;

import jakarta.persistence.EntityNotFoundException;
import us.dot.its.jpo.ode.api.mappers.FirmwareUploadMapper;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpload;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
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
    private final us.dot.its.jpo.ode.api.repositories.FirmwareImageRepository images =
            mock(us.dot.its.jpo.ode.api.repositories.FirmwareImageRepository.class);

    private FirmwareUploadService service;
    private FirmwareUploadUrlRequest request;
    private RsuModel model;

    @BeforeEach
    void setUp() {
        properties.setMaxUploadSize(DataSize.ofMegabytes(100));
        service = new FirmwareUploadService(rsuModelRepository, firmwareUploadRepository,
                objectStorageServices, properties, Mappers.getMapper(FirmwareUploadMapper.class), images,
                new FirmwareRegistrationService(firmwareUploadRepository, images, Mappers.getMapper(FirmwareUploadMapper.class)));
        when(firmwareUploadRepository.findByIdForUpdate(any())).thenAnswer(invocation ->
                firmwareUploadRepository.findById(invocation.getArgument(0)));

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
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName("Commsignia");
        manufacturer.setFirmwareFileExtension(".tar.sig");
        model.setManufacturer(manufacturer);
        when(rsuModelRepository.findByNameAndManufacturerName("ITS-RS4-M", "Commsignia"))
                .thenReturn(Optional.of(model));
        when(objectStorageService.providerName()).thenReturn("gcp");
        when(objectStorageServices.getActiveService()).thenReturn(objectStorageService);
        when(objectStorageServices.getService("gcp")).thenReturn(objectStorageService);
    }

    @Test
    void createsPendingUploadIntentAndReturnsItsId() {
        request.setVersion(" y20.97.0 ");
        request.setFileName(" rs4-generic-ro-secureboot-y20.97.0-b377993.tar.sig ");
        request.setContentType(" application/octet-stream ");
        request.setChecksumAlgorithm(" crc32c ");
        request.setChecksum(" ImIEBA== ");
        SignedUploadUrl signedUrl = new SignedUploadUrl("https://storage.googleapis.com/signed", "PUT",
                new ObjectStorageLocation("gcp", "firmware-bucket",
                        "Commsignia/ITS-RS4-M/y20.97.0/y20.97.0.tar.sig"),
                EXPIRES_AT, Map.of("x-goog-hash", "crc32c=ImIEBA=="));
        when(objectStorageService.createSignedUploadUrl(any(ObjectUploadRequest.class))).thenReturn(signedUrl);

        FirmwareUploadUrl result = service.createFirmwareSignedUploadUrl(request, "admin@example.com");

        ArgumentCaptor<ObjectUploadRequest> requestCaptor = ArgumentCaptor.forClass(ObjectUploadRequest.class);
        verify(objectStorageService).createSignedUploadUrl(requestCaptor.capture());
        assertThat(requestCaptor.getValue().objectName()).isEqualTo(
                "Commsignia/ITS-RS4-M/y20.97.0/y20.97.0.tar.sig");
        assertThat(requestCaptor.getValue().checksum()).isEqualTo(new ObjectChecksum("CRC32C", "ImIEBA=="));

        ArgumentCaptor<FirmwareUpload> uploadCaptor = ArgumentCaptor.forClass(FirmwareUpload.class);
        verify(firmwareUploadRepository).save(uploadCaptor.capture());
        FirmwareUpload upload = uploadCaptor.getValue();
        assertThat(upload.getId()).isEqualTo(result.uploadId());
        assertThat(upload.getModel()).isSameAs(model);
        assertThat(upload.getVersion()).isEqualTo("y20.97.0");
        assertThat(upload.getFileName()).isEqualTo("y20.97.0.tar.sig");
        assertThat(upload.getContentType()).isEqualTo("application/octet-stream");
        assertThat(upload.getObjectName()).isEqualTo(signedUrl.location().objectName());
        assertThat(upload.getCreatedAt()).isNotNull();
        assertThat(upload.getVerifiedAt()).isNull();
        assertThat(upload.getFinishedAt()).isNull();
        assertThat(upload.getFailureReason()).isNull();
        assertThat(upload.getObservedChecksum()).isNull();
        assertThat(upload.getProviderObjectVersion()).isNull();
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
                "Commsignia/ITS-RS4-M/y20.97.0/y20.97.0.tar.sig");
    }

    @Test
    void rejectsExistingObjectBeforeSigningOrSavingIntent() {
        when(objectStorageService.objectExists(any())).thenReturn(true);

        assertThatThrownBy(() -> service.createFirmwareSignedUploadUrl(request, "admin"))
                .isInstanceOf(FirmwareVersionAlreadyExistsException.class)
                .hasMessageContaining("already exists");

        verify(objectStorageService, never()).createSignedUploadUrl(any());
        verify(firmwareUploadRepository, never()).save(any());
    }

    @Test
    void reportsConflictWhenConcurrentRequestClaimsSameDestination() {
        SignedUploadUrl signedUrl = new SignedUploadUrl("https://storage.googleapis.com/signed", "PUT",
                new ObjectStorageLocation("gcp", "firmware-bucket",
                        "Commsignia/ITS-RS4-M/y20.97.0/y20.97.0.tar.sig"),
                EXPIRES_AT, Map.of("x-goog-hash", "crc32c=ImIEBA=="));
        when(objectStorageService.createSignedUploadUrl(any(ObjectUploadRequest.class))).thenReturn(signedUrl);
        when(firmwareUploadRepository.save(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate key violates unique constraint uq_firmware_uploads_active_destination"));

        assertThatThrownBy(() -> service.createFirmwareSignedUploadUrl(request, "admin"))
                .isInstanceOf(FirmwareVersionAlreadyExistsException.class)
                .hasMessageContaining("already exists");
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
    void rejectsFileWithoutManufacturerExtension() {
        request.setFileName("firmware.tar");

        assertThatThrownBy(() -> service.createFirmwareSignedUploadUrl(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".tar.sig")
                .hasMessageContaining("Commsignia");
        verify(objectStorageService, never()).createSignedUploadUrl(any());
    }

    @Test
    void rejectsManufacturerWithoutFirmwareUploadConfiguration() {
        model.getManufacturer().setFirmwareFileExtension(null);

        assertThatThrownBy(() -> service.createFirmwareSignedUploadUrl(request, "admin"))
                .isInstanceOf(FirmwareUploadService.FirmwareUploadConfigurationException.class)
                .hasMessageContaining("Commsignia");
        verify(objectStorageService, never()).createSignedUploadUrl(any());
    }

    @Test
    void rejectsRegisteredVersionEvenWithDifferentFilename() {
        when(images.existsByModelIdAndVersion(7, "y20.97.0")).thenReturn(true);
        request.setFileName("different.bin");
        assertThatThrownBy(() -> service.createFirmwareSignedUploadUrl(request, "admin"))
                .isInstanceOf(FirmwareVersionAlreadyExistsException.class);
        verify(objectStorageService, never()).createSignedUploadUrl(any());
        verify(firmwareUploadRepository, never()).save(any());
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
                .isInstanceOf(FirmwareUploadVerificationException.class)
                .hasMessageContaining("expected firmware file was not found in storage")
                .hasMessageContaining("signed URL completed successfully");
        assertThat(upload.getStatus()).isEqualTo(FirmwareUploadStatus.PENDING);
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
                .isInstanceOf(FirmwareUploadVerificationException.class)
                .hasMessageContaining("checksum");
        assertThat(upload.getStatus()).isEqualTo(FirmwareUploadStatus.FAILED);
        assertThat(upload.getFailureReason()).isEqualTo("CHECKSUM_MISMATCH");
        assertThat(upload.getFinishedAt()).isNotNull();
        verify(firmwareUploadRepository).save(upload);
    }

    @Test
    void rejectsCompletionWhenSizeDoesNotMatch() {
        FirmwareUpload upload = pendingUpload();
        when(firmwareUploadRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(objectStorageService.getObjectMetadata(any(ObjectStorageLocation.class), eq("CRC32C")))
                .thenReturn(Optional.of(new StoredObjectMetadata(
                        12344L, new ObjectChecksum("CRC32C", "ImIEBA=="), "17")));

        assertThatThrownBy(() -> service.completeFirmwareUpload(upload.getId()))
                .isInstanceOf(FirmwareUploadVerificationException.class)
                .hasMessageContaining("size");
        assertThat(upload.getStatus()).isEqualTo(FirmwareUploadStatus.FAILED);
        assertThat(upload.getFailureReason()).isEqualTo("SIZE_MISMATCH");
        assertThat(upload.getFinishedAt()).isNotNull();
        verify(firmwareUploadRepository).save(upload);
    }

    @Test
    void rejectsCompletionWhenChecksumAlgorithmDoesNotMatch() {
        FirmwareUpload upload = pendingUpload();
        when(firmwareUploadRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(objectStorageService.getObjectMetadata(any(ObjectStorageLocation.class), eq("CRC32C")))
                .thenReturn(Optional.of(new StoredObjectMetadata(
                        12345L, new ObjectChecksum("SHA256", "ImIEBA=="), "17")));

        assertThatThrownBy(() -> service.completeFirmwareUpload(upload.getId()))
                .isInstanceOf(FirmwareUploadVerificationException.class)
                .hasMessageContaining("checksum");
        assertThat(upload.getStatus()).isEqualTo(FirmwareUploadStatus.FAILED);
        assertThat(upload.getFailureReason()).isEqualTo("CHECKSUM_MISMATCH");
        verify(firmwareUploadRepository).save(upload);
    }

    @Test
    void rejectsCompletionWhenProviderDoesNotReturnChecksum() {
        FirmwareUpload upload = pendingUpload();
        when(firmwareUploadRepository.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(objectStorageService.getObjectMetadata(any(ObjectStorageLocation.class), eq("CRC32C")))
                .thenReturn(Optional.of(new StoredObjectMetadata(12345L, null, "17")));

        assertThatThrownBy(() -> service.completeFirmwareUpload(upload.getId()))
                .isInstanceOf(FirmwareUploadVerificationException.class)
                .hasMessageContaining("checksum");
        assertThat(upload.getStatus()).isEqualTo(FirmwareUploadStatus.FAILED);
        assertThat(upload.getFailureReason()).isEqualTo("CHECKSUM_MISMATCH");
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
