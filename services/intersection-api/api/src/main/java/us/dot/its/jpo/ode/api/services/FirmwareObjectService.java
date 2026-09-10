package us.dot.its.jpo.ode.api.services;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpload;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.models.storage.FirmwareObjectPage;
import us.dot.its.jpo.ode.api.repositories.FirmwareImageRepository;
import us.dot.its.jpo.ode.api.repositories.FirmwareUploadRepository;
import us.dot.its.jpo.ode.api.storage.ObjectStorageServiceRegistry;

@Service
@RequiredArgsConstructor
public class FirmwareObjectService {
    private final ObjectStorageServiceRegistry storage;
    private final FirmwareUploadRepository uploads;
    private final FirmwareImageRepository images;

    public FirmwareObjectPage list(int pageSize, String pageToken) {
        if (pageSize < 1 || pageSize > 200) {
            throw new IllegalArgumentException("page_size must be between 1 and 200");
        }

        var page = storage.getActiveService().listObjects(pageSize, pageToken);
        if (page.objects().isEmpty()) {
            return new FirmwareObjectPage(page.provider(), page.container(), List.of(), page.nextPageToken());
        }

        // Join only the current storage page, keeping database work bounded too.
        var records = uploads.findListingUploads(page.provider(), page.container(),
                page.objects().stream().map(item -> item.objectName()).toList());
        var byName = records.stream().collect(Collectors.toMap(FirmwareUpload::getObjectName, Function.identity()));
        Map<UUID, Integer> imageIds = records.isEmpty() ? Map.of() : images.findByVerifiedUploadIdIn(
                records.stream().map(FirmwareUpload::getId).toList()).stream()
                .collect(Collectors.toMap(image -> image.getVerifiedUpload().getId(), image -> image.getId()));

        var items = page.objects().stream().map(object -> {
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
                    (page.provider() + "\n" + page.container() + "\n" + object.objectName()).getBytes(StandardCharsets.UTF_8));
            return new FirmwareObjectPage.Item(id, object.objectName(), object.contentLength(), object.updatedAt(),
                    object.providerObjectVersion(), upload == null ? null : upload.getId(),
                    upload == null ? null : imageIds.get(upload.getId()), upload == null ? null : upload.getStatus().name(), state);
        }).toList();

        return new FirmwareObjectPage(page.provider(), page.container(), items, page.nextPageToken());
    }
}
