package us.dot.its.jpo.ode.api.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import us.dot.its.jpo.ode.api.models.organizations.OrganizationDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrganizationMapper {

    /**
     * Convert Organization entity to OrganizationDto.
     * Field names match exactly, so no explicit @Mapping annotations are needed.
     */
    OrganizationDto toDto(Organization organization);
}
