package us.dot.its.jpo.ode.api.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;

import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.ObjectChecksum;
import us.dot.its.jpo.ode.api.models.storage.ObjectStorageLocation;
import us.dot.its.jpo.ode.api.models.storage.ObjectUploadRequest;

class GcpObjectStorageServiceTest {
    private final GcpStorageClientProvider clientProvider = mock(GcpStorageClientProvider.class);
    private final Storage storage = mock(Storage.class);
    private final ObjectStorageProperties properties = new ObjectStorageProperties();
    private final GcpObjectStorageProperties gcpProperties = new GcpObjectStorageProperties();
    private final GoogleCredentials credentials = mock(GoogleCredentials.class,
            withSettings().extraInterfaces(ServiceAccountSigner.class));

    private GcpObjectStorageService service;
    private ObjectUploadRequest request;

    @BeforeEach
    void setUp() throws Exception {
        gcpProperties.setBucketName("firmware-bucket");
        properties.setSignedUrlExpiration(Duration.ofMinutes(15));
        service = new GcpObjectStorageService(clientProvider, properties, gcpProperties);

        request = new ObjectUploadRequest("Acme/RoadRunner/y20.97.0/firmware.bin", 12345L,
                "application/octet-stream", new ObjectChecksum("CRC32C", "ImIEBA=="));

        when(clientProvider.getCredentials()).thenReturn(credentials);
        when(clientProvider.getStorage()).thenReturn(storage);
    }

    @Test
    void createsV4PutUrlForExpectedObjectWithoutOverwrite() throws Exception {
        URL url = new URL("https://storage.googleapis.com/upload?signature=test");
        Storage.SignUrlOption putOption = mock(Storage.SignUrlOption.class);
        Storage.SignUrlOption v4Option = mock(Storage.SignUrlOption.class);
        Storage.SignUrlOption headersOption = mock(Storage.SignUrlOption.class);
        Storage.SignUrlOption signerOption = mock(Storage.SignUrlOption.class);
        Map<String, String> headers = Map.of(
                "Content-Type", "application/octet-stream",
                "x-goog-if-generation-match", "0",
                "x-goog-content-length-range", "12345,12345",
                "x-goog-hash", "crc32c=ImIEBA==");

        try (MockedStatic<Storage.SignUrlOption> options = mockStatic(Storage.SignUrlOption.class)) {
            options.when(() -> Storage.SignUrlOption.httpMethod(HttpMethod.PUT)).thenReturn(putOption);
            options.when(Storage.SignUrlOption::withV4Signature).thenReturn(v4Option);
            options.when(() -> Storage.SignUrlOption.withExtHeaders(headers)).thenReturn(headersOption);
            options.when(() -> Storage.SignUrlOption.signWith((ServiceAccountSigner) credentials))
                    .thenReturn(signerOption);
            when(storage.signUrl(any(BlobInfo.class), anyLong(), any(TimeUnit.class),
                    any(Storage.SignUrlOption[].class))).thenReturn(url);

            SignedUploadUrl result = service.createSignedUploadUrl(request);

            ArgumentCaptor<BlobInfo> blobCaptor = ArgumentCaptor.forClass(BlobInfo.class);
            verify(storage).signUrl(blobCaptor.capture(), eq(900L), eq(TimeUnit.SECONDS),
                    eq(new Storage.SignUrlOption[] { putOption, v4Option, headersOption, signerOption }));
            options.verify(() -> Storage.SignUrlOption.httpMethod(HttpMethod.PUT));
            options.verify(Storage.SignUrlOption::withV4Signature);
            options.verify(() -> Storage.SignUrlOption.withExtHeaders(headers));

            assertThat(blobCaptor.getValue().getBlobId().getBucket()).isEqualTo("firmware-bucket");
            assertThat(blobCaptor.getValue().getBlobId().getName())
                    .isEqualTo("Acme/RoadRunner/y20.97.0/firmware.bin");
            assertThat(result.uploadUrl()).isEqualTo(url.toString());
            assertThat(result.method()).isEqualTo("PUT");
            assertThat(result.location()).isEqualTo(new ObjectStorageLocation(
                    "gcp", "firmware-bucket", "Acme/RoadRunner/y20.97.0/firmware.bin"));
            assertThat(result.requiredHeaders()).containsEntry("Content-Type", "application/octet-stream")
                    .containsEntry("x-goog-if-generation-match", "0")
                    .containsEntry("x-goog-content-length-range", "12345,12345")
                    .containsEntry("x-goog-hash", "crc32c=ImIEBA==");
        }
    }

    @Test
    void readsMetadataUsedForCompletionVerification() throws Exception {
        Blob blob = mock(Blob.class);
        when(blob.getSize()).thenReturn(12345L);
        when(blob.getCrc32c()).thenReturn("ImIEBA==");
        when(blob.getGeneration()).thenReturn(17L);
        when(storage.get(any(BlobId.class), any(Storage.BlobGetOption[].class))).thenReturn(blob);

        var metadata = service.getObjectMetadata(new ObjectStorageLocation(
                "gcp", "firmware-bucket", "Acme/RoadRunner/y20.97.0/firmware.bin"), "CRC32C");

        assertThat(metadata).hasValueSatisfying(value -> {
            assertThat(value.contentLength()).isEqualTo(12345L);
            assertThat(value.checksum()).isEqualTo(new ObjectChecksum("CRC32C", "ImIEBA=="));
            assertThat(value.providerObjectVersion()).isEqualTo("17");
        });
    }

    @Test
    void returnsEmptyWhenUploadedObjectDoesNotExist() throws Exception {
        when(storage.get(any(BlobId.class), any(Storage.BlobGetOption[].class))).thenReturn(null);

        assertThat(service.getObjectMetadata(
                new ObjectStorageLocation("gcp", "firmware-bucket", "missing.bin"), "CRC32C")).isEmpty();
    }

    @Test
    void rejectsUnsupportedChecksumAlgorithmBeforeCallingGoogleCloud() {
        request = new ObjectUploadRequest(request.objectName(), request.contentLength(), request.contentType(),
                new ObjectChecksum("SHA256", "ImIEBA=="));

        assertThatThrownBy(() -> service.createSignedUploadUrl(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CRC32C");
    }

    @Test
    void rejectsInvalidCrc32cEncodingBeforeCallingGoogleCloud() {
        request = new ObjectUploadRequest(request.objectName(), request.contentLength(), request.contentType(),
                new ObjectChecksum("CRC32C", "not-a-checksum"));

        assertThatThrownBy(() -> service.createSignedUploadUrl(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base64-encoded");
    }

    @Test
    void rejectsObjectNamesOverGcsUtf8ByteLimit() {
        request = new ObjectUploadRequest("\u20ac".repeat(400), request.contentLength(), request.contentType(),
                request.checksum());

        assertThatThrownBy(() -> service.createSignedUploadUrl(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1024 UTF-8 bytes");
    }

    @Test
    void convertsGoogleCloudErrorsToStandardResponseStatusException() throws Exception {
        when(storage.signUrl(any(BlobInfo.class), anyLong(), any(TimeUnit.class),
                any(Storage.SignUrlOption[].class))).thenThrow(new IllegalStateException("secret SDK detail"));

        assertThatThrownBy(() -> service.createSignedUploadUrl(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason()).isEqualTo("Unable to generate signed upload URL");
                });
    }
}
