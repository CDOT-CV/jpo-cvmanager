package us.dot.its.jpo.ode.api.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareImage;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpgradeRule;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpload;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.models.storage.FirmwareRuleModels.Image;
import us.dot.its.jpo.ode.api.models.storage.FirmwareRuleModels.Rule;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FirmwareRuleMapper {
    @Mapping(target = "firmwareId", source = "id")
    @Mapping(target = "manufacturer", source = "model.manufacturer.name")
    @Mapping(target = "model", source = "model.name")
    @Mapping(target = "legacy", expression = "java(image.getVerifiedUpload() == null)")
    Image toImage(FirmwareImage image);

    @Mapping(target = "ruleId", source = "id")
    @Mapping(target = "source", source = "from")
    @Mapping(target = "destination", source = "to")
    @Mapping(target = "legacyDestination", source = "to.verifiedUpload", qualifiedByName = "legacyDestination")
    Rule toRule(FirmwareUpgradeRule rule);

    @Named("legacyDestination")
    default boolean legacyDestination(FirmwareUpload upload) {
        return upload == null || upload.getStatus() != FirmwareUploadStatus.VERIFIED;
    }
}
