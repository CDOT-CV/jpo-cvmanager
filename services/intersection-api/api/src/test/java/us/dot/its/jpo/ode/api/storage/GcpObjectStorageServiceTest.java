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
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;

import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrlRequest;

class GcpObjectStorageServiceTest {
    private final GcpStorageClientProvider clientProvider = mock(GcpStorageClientProvider.class);
    private final Storage storage = mock(Storage.class);
    private final ObjectStorageProperties properties = new ObjectStorageProperties();
    private final GoogleCredentials credentials = mock(GoogleCredentials.class,
            withSettings().extraInterfaces(ServiceAccountSigner.class));

    private GcpObjectStorageService service;
    private SignedUploadUrlRequest request;

    @BeforeEach
    void setUp() throws Exception {
        properties.getGcp().setBucketName("firmware-bucket");
        properties.setSignedUrlExpiration(Duration.ofMinutes(15));
        service = new GcpObjectStorageService(clientProvider, properties);

        request = new SignedUploadUrlRequest();
        request.setVendorName("Acme");
        request.setModelName("RoadRunner");
        request.setVersion("y20.97.0");
        request.setFileName("firmware.bin");
        request.setContentType("application/octet-stream");
        request.setContentLength(12345L);

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
                "x-goog-content-length-range", "12345,12345");

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
            assertThat(result.requiredHeaders()).containsEntry("Content-Type", "application/octet-stream")
                    .containsEntry("x-goog-if-generation-match", "0")
                    .containsEntry("x-goog-content-length-range", "12345,12345");
        }
    }

    @Test
    void rejectsPathTraversalSegmentsBeforeCallingGoogleCloud() {
        request.setFileName("../firmware.bin");

        assertThatThrownBy(() -> service.createSignedUploadUrl(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file_name");
    }

    @Test
    void rejectsObjectNamesOverGcsUtf8ByteLimit() {
        request.setVendorName("\u20ac".repeat(400));

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
