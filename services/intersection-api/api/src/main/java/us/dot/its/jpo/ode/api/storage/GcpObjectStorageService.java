package us.dot.its.jpo.ode.api.storage;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.ObjectChecksum;
import us.dot.its.jpo.ode.api.models.storage.ObjectStorageLocation;
import us.dot.its.jpo.ode.api.models.storage.ObjectUploadRequest;
import us.dot.its.jpo.ode.api.models.storage.StoredObjectMetadata;

/**
 * Google Cloud Storage adapter for the provider-neutral object-storage
 * contract. GCS specific credentials, headers, limits, and CRC32C encoding are
 * intentionally contained in this class.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GcpObjectStorageService implements ObjectStorageService {
    private static final String PROVIDER_NAME = "gcp";
    private static final String CRC32C = "CRC32C";
    private static final int CRC32C_BYTES = 4;
    static final String CONTENT_TYPE_HEADER = "Content-Type";
    static final String DOES_NOT_EXIST_HEADER = "x-goog-if-generation-match";
    static final String CONTENT_LENGTH_RANGE_HEADER = "x-goog-content-length-range";
    static final String HASH_HEADER = "x-goog-hash";

    private final GcpStorageClientProvider clientProvider;
    private final ObjectStorageProperties properties;
    private final GcpObjectStorageProperties gcpProperties;

    @Override
    public boolean objectExists(String objectName) {
        validateConfiguration();
        String validatedObjectName = validateObjectName(objectName);
        try {
            Blob blob = clientProvider.getStorage().get(
                    BlobId.of(gcpProperties.getBucketName().trim(), validatedObjectName),
                    Storage.BlobGetOption.fields(Storage.BlobField.NAME));
            return blob != null;
        } catch (Exception ex) {
            log.error("Failed to check whether object {} exists", validatedObjectName, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to check whether the firmware object already exists");
        }
    }

    @Override
    public SignedUploadUrl createSignedUploadUrl(ObjectUploadRequest request) {
        validateConfiguration();
        if (request.contentLength() <= 0) {
            throw new IllegalArgumentException("content_length must be greater than zero");
        }
        // GCS validates CRC32C server-side when this checksum is included in the
        // signed x-goog-hash upload header
        ObjectChecksum checksum = validateChecksum(request.checksum());
        String objectName = validateObjectName(request.objectName());
        Duration expiration = properties.getSignedUrlExpiration();
        // Every entry is included in the V4 signature and must be sent unchanged by
        // the uploading client
        Map<String, String> requiredHeaders = Map.of(
                CONTENT_TYPE_HEADER, request.contentType().trim(),
                DOES_NOT_EXIST_HEADER, "0",
                CONTENT_LENGTH_RANGE_HEADER, request.contentLength() + "," + request.contentLength(),
                HASH_HEADER, "crc32c=" + checksum.value());

        try {
            GoogleCredentials credentials = clientProvider.getCredentials();
            ServiceAccountSigner signer = resolveSigner(credentials);
            Storage storage = clientProvider.getStorage();
            ObjectStorageLocation location = new ObjectStorageLocation(
                    PROVIDER_NAME, gcpProperties.getBucketName().trim(), objectName);
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(location.container(), objectName))
                    .setContentType(request.contentType().trim())
                    .build();

            URL url = storage.signUrl(
                    blobInfo,
                    expiration.toSeconds(),
                    TimeUnit.SECONDS,
                    Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                    Storage.SignUrlOption.withV4Signature(),
                    Storage.SignUrlOption.withExtHeaders(requiredHeaders),
                    Storage.SignUrlOption.signWith(signer));

            return new SignedUploadUrl(url.toString(), "PUT", location,
                    Instant.now().plus(expiration), requiredHeaders);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to create a signed upload URL for object {}", objectName, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to generate signed upload URL");
        }
    }

    @Override
    public Optional<StoredObjectMetadata> getObjectMetadata(
            ObjectStorageLocation location, String checksumAlgorithm) {
        validateLocation(location);
        validateChecksumAlgorithm(checksumAlgorithm);
        try {
            Blob blob = clientProvider.getStorage().get(
                    BlobId.of(location.container(), location.objectName()),
                    Storage.BlobGetOption.fields(Storage.BlobField.SIZE, Storage.BlobField.CRC32C,
                            Storage.BlobField.GENERATION));
            if (blob == null) {
                return Optional.empty();
            }
            Long generation = blob.getGeneration();
            // Convert numeric GCS generations into the contract's opaque provider
            // version so other providers can return their native version identifiers
            return Optional.of(new StoredObjectMetadata(blob.getSize(),
                    new ObjectChecksum(CRC32C, blob.getCrc32c()),
                    generation == null ? null : generation.toString()));
        } catch (Exception ex) {
            log.error("Failed to read object metadata for {}", location.objectName(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to verify uploaded object");
        }
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    private ServiceAccountSigner resolveSigner(GoogleCredentials credentials) {
        // Service-account key credentials can sign locally. Workload Identity and
        // user ADC instead sign through the configured impersonated service account
        if (credentials instanceof ServiceAccountSigner signer) {
            return signer;
        }

        String signingServiceAccount = gcpProperties.getSigningServiceAccount();
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

    private String validateObjectName(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            throw new IllegalArgumentException("object_name is required");
        }
        if (objectName.getBytes(StandardCharsets.UTF_8).length > 1024) {
            throw new IllegalArgumentException("GCP object name must not exceed 1024 UTF-8 bytes");
        }
        return objectName;
    }

    private ObjectChecksum validateChecksum(ObjectChecksum checksum) {
        if (checksum == null) {
            throw new IllegalArgumentException("checksum is required");
        }
        validateChecksumAlgorithm(checksum.algorithm());
        String value = checksum.value() == null ? "" : checksum.value().trim();
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length != CRC32C_BYTES
                    || !Base64.getEncoder().encodeToString(decoded).equals(value)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "checksum must be a canonical base64-encoded 32-bit CRC32C value");
        }
        return new ObjectChecksum(CRC32C, value);
    }

    private void validateChecksumAlgorithm(String algorithm) {
        if (!CRC32C.equalsIgnoreCase(algorithm == null ? "" : algorithm.trim())) {
            throw new IllegalArgumentException("GCP object storage supports the CRC32C checksum algorithm");
        }
    }

    private void validateLocation(ObjectStorageLocation location) {
        if (location == null || !PROVIDER_NAME.equalsIgnoreCase(location.provider())
                || !StringUtils.hasText(location.container())
                || !StringUtils.hasText(location.objectName())) {
            throw new IllegalArgumentException("Object storage location is not valid for GCP");
        }
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(gcpProperties.getBucketName())) {
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
