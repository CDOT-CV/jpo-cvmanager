package us.dot.its.jpo.ode.api.storage;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrlRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class GcpObjectStorageService implements ObjectStorageService {
    static final String CONTENT_TYPE_HEADER = "Content-Type";
    static final String DOES_NOT_EXIST_HEADER = "x-goog-if-generation-match";
    static final String CONTENT_LENGTH_RANGE_HEADER = "x-goog-content-length-range";

    private final GcpStorageClientProvider clientProvider;
    private final ObjectStorageProperties properties;

    @Override
    public SignedUploadUrl createSignedUploadUrl(SignedUploadUrlRequest request) {
        validateConfiguration();
        if (request.getContentLength() == null || request.getContentLength() <= 0) {
            throw new IllegalArgumentException("content_length must be greater than zero");
        }
        String objectName = buildObjectName(request);
        Duration expiration = properties.getSignedUrlExpiration();
        Map<String, String> requiredHeaders = Map.of(
                CONTENT_TYPE_HEADER, request.getContentType().trim(),
                DOES_NOT_EXIST_HEADER, "0",
                CONTENT_LENGTH_RANGE_HEADER, request.getContentLength() + "," + request.getContentLength());

        try {
            GoogleCredentials credentials = clientProvider.getCredentials();
            ServiceAccountSigner signer = resolveSigner(credentials);
            Storage storage = clientProvider.getStorage();
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(properties.getGcp().getBucketName().trim(), objectName))
                    .setContentType(request.getContentType().trim())
                    .build();

            URL url = storage.signUrl(
                    blobInfo,
                    expiration.toSeconds(),
                    TimeUnit.SECONDS,
                    Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                    Storage.SignUrlOption.withV4Signature(),
                    Storage.SignUrlOption.withExtHeaders(requiredHeaders),
                    Storage.SignUrlOption.signWith(signer));

            return new SignedUploadUrl(url.toString(), "PUT", objectName,
                    Instant.now().plus(expiration), requiredHeaders);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to create a signed upload URL for object {}", objectName, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to generate signed upload URL");
        }
    }

    private ServiceAccountSigner resolveSigner(GoogleCredentials credentials) {
        if (credentials instanceof ServiceAccountSigner signer) {
            return signer;
        }

        String signingServiceAccount = properties.getGcp().getSigningServiceAccount();
        if (!StringUtils.hasText(signingServiceAccount)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Object storage signing service account is not configured");
        }

        return ImpersonatedCredentials.create(
                credentials,
                signingServiceAccount.trim(),
                null,
                List.of("https://www.googleapis.com/auth/cloud-platform"),
                3600);
    }

    private String buildObjectName(SignedUploadUrlRequest request) {
        String objectName = String.join("/",
                validateSegment(request.getVendorName(), "vendor_name"),
                validateSegment(request.getModelName(), "model_name"),
                validateSegment(request.getVersion(), "version"),
                validateSegment(request.getFileName(), "file_name"));
        if (objectName.getBytes(StandardCharsets.UTF_8).length > 1024) {
            throw new IllegalArgumentException("Object name must not exceed 1024 UTF-8 bytes");
        }
        return objectName;
    }

    private String validateSegment(String value, String fieldName) {
        String segment = value == null ? "" : value.trim();
        if (!StringUtils.hasText(segment)
                || ".".equals(segment)
                || "..".equals(segment)
                || segment.indexOf('/') >= 0
                || segment.indexOf('\\') >= 0
                || segment.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(fieldName + " must be a valid object path segment");
        }
        return segment;
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getGcp().getBucketName())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Object storage bucket is not configured");
        }
        Duration expiration = properties.getSignedUrlExpiration();
        if (expiration == null || expiration.isZero() || expiration.isNegative()
                || expiration.compareTo(Duration.ofDays(7)) > 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Object storage signed URL expiration must be between 1 second and 7 days");
        }
    }
}
