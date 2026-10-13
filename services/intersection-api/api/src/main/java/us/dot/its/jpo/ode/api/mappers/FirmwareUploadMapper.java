package us.dot.its.jpo.ode.api.mappers;

import java.time.Instant;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpload;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareImage;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadUrlRequest;
import us.dot.its.jpo.ode.api.models.storage.ObjectChecksum;
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrl;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FirmwareUploadMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", source = "version")
    @Mapping(target = "installPackage", source = "fileName")
    @Mapping(target = "verifiedUpload", source = "upload")
    FirmwareImage toFirmwareImage(FirmwareUpload upload);

    @Mapping(target = "id", source = "uploadId")
    @Mapping(target = "model", source = "model")
    @Mapping(target = "version", source = "request.version", qualifiedByName = "trim")
    @Mapping(target = "fileName", source = "storedFileName")
    @Mapping(target = "contentType", source = "request.contentType", qualifiedByName = "trim")
    @Mapping(target = "storageProvider", source = "signedUrl.location.provider")
    @Mapping(target = "storageContainer", source = "signedUrl.location.container")
    @Mapping(target = "objectName", source = "signedUrl.location.objectName")
    @Mapping(target = "expectedSize", source = "request.contentLength")
    @Mapping(target = "checksumAlgorithm", source = "checksum.algorithm")
    @Mapping(target = "expectedChecksum", source = "checksum.value")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "expiresAt", source = "signedUrl.expiresAt")
    @Mapping(target = "verifiedAt", ignore = true)
    @Mapping(target = "finishedAt", ignore = true)
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "providerObjectVersion", ignore = true)
    @Mapping(target = "observedChecksum", ignore = true)
    FirmwareUpload toEntity(FirmwareUploadUrlRequest request, RsuModel model,
            SignedUploadUrl signedUrl, ObjectChecksum checksum, UUID uploadId,
            String createdBy, Instant createdAt, String storedFileName);

    @Named("trim")
    default String trim(String value) {
        return value == null ? null : value.trim();
    }
}
