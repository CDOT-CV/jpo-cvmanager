package us.dot.its.jpo.ode.api.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import us.dot.its.jpo.ode.api.models.postgres.tables.ScmsHealth;
import us.dot.its.jpo.ode.api.models.scms.ScmsHealthDto;

import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ScmsHealthMapper {
    @Mapping(target = "rsuId", source = "rsu.id")
    ScmsHealthDto toDto(ScmsHealth scmsHealth);

    List<ScmsHealthDto> toDtoList(List<ScmsHealth> scmsHealth);
}
