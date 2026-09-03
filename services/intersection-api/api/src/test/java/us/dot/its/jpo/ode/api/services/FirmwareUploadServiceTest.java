package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import jakarta.persistence.EntityNotFoundException;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrlRequest;
import us.dot.its.jpo.ode.api.repositories.RsuModelRepository;
import us.dot.its.jpo.ode.api.storage.ObjectStorageProperties;
import us.dot.its.jpo.ode.api.storage.ObjectStorageService;

class FirmwareUploadServiceTest {
    private final RsuModelRepository rsuModelRepository = mock(RsuModelRepository.class);
    private final ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
    private final ObjectStorageProperties properties = new ObjectStorageProperties();

    private FirmwareUploadService service;
    private SignedUploadUrlRequest request;

    @BeforeEach
    void setUp() {
        properties.setMaxUploadSize(DataSize.ofMegabytes(100));
        service = new FirmwareUploadService(rsuModelRepository, objectStorageService, properties);

        request = new SignedUploadUrlRequest();
        request.setVendorName("Commsignia");
        request.setModelName("ITS-RS4-M");
        request.setVersion("y20.97.0");
        request.setFileName("rs4-generic-ro-secureboot-y20.97.0-b377993.tar.sig");
        request.setContentLength(50L * 1024 * 1024);
        request.setContentType("application/octet-stream");
    }

    @Test
    void delegatesWhenVendorModelPairExistsAndSizeIsAllowed() {
        RsuModel model = new RsuModel();
        SignedUploadUrl expected = mock(SignedUploadUrl.class);
        when(rsuModelRepository.findByNameAndManufacturerName("ITS-RS4-M", "Commsignia"))
                .thenReturn(Optional.of(model));
        when(objectStorageService.createSignedUploadUrl(request)).thenReturn(expected);

        assertThat(service.createFirmwareSignedUploadUrl(request)).isSameAs(expected);
        verify(objectStorageService).createSignedUploadUrl(request);
    }

    @Test
    void rejectsUnknownVendorModelPair() {
        when(rsuModelRepository.findByNameAndManufacturerName("ITS-RS4-M", "Unknown"))
                .thenReturn(Optional.empty());
        request.setVendorName("Unknown");

        assertThatThrownBy(() -> service.createFirmwareSignedUploadUrl(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("ITS-RS4-M")
                .hasMessageContaining("Unknown");
        verify(objectStorageService, never()).createSignedUploadUrl(request);
    }

    @Test
    void rejectsUploadsLargerThanConfiguredMaximum() {
        when(rsuModelRepository.findByNameAndManufacturerName("ITS-RS4-M", "Commsignia"))
                .thenReturn(Optional.of(new RsuModel()));
        request.setContentLength(DataSize.ofMegabytes(100).toBytes() + 1);

        assertThatThrownBy(() -> service.createFirmwareSignedUploadUrl(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content_length")
                .hasMessageContaining(String.valueOf(DataSize.ofMegabytes(100).toBytes()));
        verify(objectStorageService, never()).createSignedUploadUrl(request);
    }
}
