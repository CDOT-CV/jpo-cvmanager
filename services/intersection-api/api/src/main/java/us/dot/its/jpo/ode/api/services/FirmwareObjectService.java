package us.dot.its.jpo.ode.api.services;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpload;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.models.storage.FirmwareObjectPage;
import us.dot.its.jpo.ode.api.models.storage.ObjectListRequest;
import us.dot.its.jpo.ode.api.models.storage.StorageObject;
import us.dot.its.jpo.ode.api.repositories.FirmwareImageRepository;
import us.dot.its.jpo.ode.api.repositories.FirmwareUploadRepository;
import us.dot.its.jpo.ode.api.storage.ObjectStorageServiceRegistry;
import us.dot.its.jpo.ode.api.storage.ObjectStorageUnavailableException;

@Service
@RequiredArgsConstructor
public class FirmwareObjectService {
    private static final int PROVIDER_PAGE_SIZE = 200;
    private static final Set<String> RESERVED_PREFIXES = Set.of("ota");

    private final ObjectStorageServiceRegistry storage;
    private final FirmwareUploadRepository uploads;
    private final FirmwareImageRepository images;

    public FirmwareObjectPage list(String manufacturer) {
        String prefix = manufacturer == null ? null : validateManufacturer(manufacturer) + "/";
        boolean recursive = prefix != null;
        var storageService = storage.getActiveService();
        var objects = new ArrayList<StorageObject>();
        var visitedPageTokens = new HashSet<String>();
        String pageToken = null;
        String provider;
        String container;

        // Root listings request direct children for manufacturer discovery.
        // Manufacturer listings collect every page in the recursive subtree.
        do {
            var page = storageService.listObjects(
                    new ObjectListRequest(prefix, recursive, PROVIDER_PAGE_SIZE, pageToken));
            provider = page.provider();
            container = page.container();
            objects.addAll(page.objects());
            pageToken = page.nextPageToken() == null || page.nextPageToken().isBlank()
                    ? null : page.nextPageToken();

            if (pageToken != null && !visitedPageTokens.add(pageToken)) {
                throw new ObjectStorageUnavailableException("Object storage returned invalid pagination state");
            }
        } while (pageToken != null);

        if (prefix == null) {
            objects.removeIf(object -> isReservedRootPrefix(object.objectName()));
        }

        if (objects.isEmpty()) {
            return new FirmwareObjectPage(storageService.providerName(), List.of());
        }

        String listingProvider = provider;
        String listingContainer = container;

        // Join the selected manufacturer subtree with upload and image evidence.
        var records = uploads.findListingUploads(listingProvider, listingContainer,
                objects.stream().map(item -> item.objectName()).toList());
        var byName = records.stream().collect(Collectors.toMap(FirmwareUpload::getObjectName, Function.identity()));
        Map<UUID, Integer> imageIds = records.isEmpty() ? Map.of() : images.findByVerifiedUploadIdIn(
                records.stream().map(FirmwareUpload::getId).toList()).stream()
                .collect(Collectors.toMap(image -> image.getVerifiedUpload().getId(), image -> image.getId()));

        var items = objects.stream().map(object -> {
            var upload = byName.get(object.objectName());
            String state = upload == null ? "UNTRACKED" : "UNVERIFIED";

            if (upload != null && upload.getStatus() == FirmwareUploadStatus.VERIFIED) {
                // Verification belongs to the exact object version, not merely its path.
                boolean matches = object.providerObjectVersion() != null
                        && object.providerObjectVersion().equals(upload.getProviderObjectVersion())
                        && Objects.equals(upload.getExpectedSize(), object.contentLength())
                        && object.checksum() != null
                        && upload.getObservedChecksum() != null
                        && upload.getChecksumAlgorithm().equalsIgnoreCase(object.checksum().algorithm())
                        && Objects.equals(upload.getObservedChecksum(), object.checksum().value());
                state = matches ? "VERIFIED" : "CHANGED";
            }

            String id = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (listingProvider + "\n" + object.objectName()).getBytes(StandardCharsets.UTF_8));
            return new FirmwareObjectPage.Item(id, object.objectName(), object.contentLength(), object.updatedAt(),
                    object.providerObjectVersion(), upload == null ? null : upload.getId(),
                    upload == null ? null : imageIds.get(upload.getId()), upload == null ? null : upload.getStatus().name(), state);
        }).toList();

        return new FirmwareObjectPage(listingProvider, items);
    }

    private String validateManufacturer(String manufacturer) {
        String normalized = manufacturer.trim();
        if (normalized.isEmpty() || normalized.length() > 128
                || normalized.chars().anyMatch(character -> character == '/' || character == '\\'
                        || Character.isISOControl(character))) {
            throw new IllegalArgumentException("manufacturer must be a valid object path segment");
        }
        if (RESERVED_PREFIXES.contains(normalized.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("manufacturer is reserved for a separate firmware workflow");
        }
        return normalized;
    }

    private boolean isReservedRootPrefix(String objectName) {
        String normalized = objectName.endsWith("/")
                ? objectName.substring(0, objectName.length() - 1) : objectName;
        return !normalized.contains("/")
                && RESERVED_PREFIXES.contains(normalized.toLowerCase(Locale.ROOT));
    }
}
