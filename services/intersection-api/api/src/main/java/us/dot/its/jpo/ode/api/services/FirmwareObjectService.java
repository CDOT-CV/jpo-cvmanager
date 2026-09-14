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
import us.dot.its.jpo.ode.api.models.storage.StorageObjectPage;
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

    public FirmwareObjectPage list(int pageNumber, int pageSize, String manufacturer, String search) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (pageSize < 1 || pageSize > 200) {
            throw new IllegalArgumentException("size must be between 1 and 200");
        }

        // Limit the provider query when a manufacturer is selected, then remove
        // folder markers and the OBU hierarchy from the RSU firmware results
        String prefix = manufacturer == null || manufacturer.isBlank()
                ? null
                : validateManufacturer(manufacturer) + "/";
        String searchTerm = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        var storageService = storage.getActiveService();
        var objects = new ArrayList<StorageObject>();
        var visitedTokens = new HashSet<String>();
        long offset = (long) pageNumber * pageSize;
        long totalElements = 0;
        String pageToken = null;
        StorageObjectPage page;

        // Search every provider page before applying table pagination, including
        // untracked files. Count all matches but retain only the requested rows.
        do {
            page = storageService.listObjects(new ObjectListRequest(prefix, PROVIDER_PAGE_SIZE, pageToken));
            for (var object : page.objects()) {
                String name = object.objectName();
                if (name.endsWith("/") || isReservedObject(name)
                        || !name.toLowerCase(Locale.ROOT).contains(searchTerm)) {
                    continue;
                }
                if (totalElements >= offset && objects.size() < pageSize) {
                    objects.add(object);
                }
                totalElements++;
            }

            pageToken = page.nextPageToken();
            if (pageToken != null && !pageToken.isBlank() && !visitedTokens.add(pageToken)) {
                throw new ObjectStorageUnavailableException("Object storage returned invalid pagination state");
            }
        } while (pageToken != null && !pageToken.isBlank());

        if (objects.isEmpty()) {
            return new FirmwareObjectPage(page.provider(), List.of(), totalElements);
        }
        String provider = page.provider();

        // Attach the best upload record and registered image, when present, to each
        // object returned by the storage provider
        var records = uploads.findListingUploads(page.provider(), page.container(),
                objects.stream().map(item -> item.objectName()).toList());
        var byName = records.stream().collect(Collectors.toMap(FirmwareUpload::getObjectName, Function.identity()));
        Map<UUID, Integer> imageIds = records.isEmpty() ? Map.of()
                : images.findByVerifiedUploadIdIn(
                        records.stream().map(FirmwareUpload::getId).toList()).stream()
                        .collect(Collectors.toMap(image -> image.getVerifiedUpload().getId(), image -> image.getId()));

        var items = objects.stream().map(object -> {
            var upload = byName.get(object.objectName());
            String state = upload == null ? "UNTRACKED" : "UNVERIFIED";

            if (upload != null && upload.getStatus() == FirmwareUploadStatus.VERIFIED) {
                // Verification belongs to the exact object version, not just its path
                boolean matches = object.providerObjectVersion() != null
                        && object.providerObjectVersion().equals(upload.getProviderObjectVersion())
                        && Objects.equals(upload.getExpectedSize(), object.contentLength())
                        && object.checksum() != null
                        && upload.getObservedChecksum() != null
                        && upload.getChecksumAlgorithm().equalsIgnoreCase(object.checksum().algorithm())
                        && Objects.equals(upload.getObservedChecksum(), object.checksum().value());
                state = matches ? "VERIFIED" : "CHANGED";
            }

            // Expose the conventional manufacturer/model/version/file path as table
            // columns while retaining the complete object name for later actions
            String id = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (provider + "\n" + object.objectName()).getBytes(StandardCharsets.UTF_8));
            String[] path = object.objectName().split("/", 4);
            String manufacturerName = path.length == 4 ? path[0] : null;
            String modelName = path.length == 4 ? path[1] : null;
            String version = path.length == 4 ? path[2] : null;
            String fileName = path.length == 4 ? path[3] : object.objectName();

            return new FirmwareObjectPage.Item(id, object.objectName(), manufacturerName, modelName,
                    version, fileName, object.contentLength(), object.updatedAt(),
                    object.providerObjectVersion(), upload == null ? null : upload.getId(),
                    upload == null ? null : imageIds.get(upload.getId()),
                    upload == null ? null : upload.getStatus().name(), state);
        }).toList();

        return new FirmwareObjectPage(provider, items, totalElements);
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

    private boolean isReservedObject(String objectName) {
        int separator = objectName.indexOf('/');
        String root = separator < 0 ? objectName : objectName.substring(0, separator);
        return RESERVED_PREFIXES.contains(root.toLowerCase(Locale.ROOT));
    }
}
