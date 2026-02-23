package us.dot.its.jpo.ode.api.mappers;

import java.util.List;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import us.dot.its.jpo.ode.api.models.postgres.dtos.UserDto;
import us.dot.its.jpo.ode.api.models.postgres.dtos.UserOrganizationDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.User;
import us.dot.its.jpo.ode.api.models.postgres.tables.UserOrganization;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    /**
     * Convert User entity to UserDto
     * MapStruct will automatically map fields with the same name
     */
    @Mapping(source = "userOrganizations", target = "organizations", qualifiedByName = "mapOrganizations")
    UserDto toDto(User user);

    /**
     * Extract organization names from RsuOrganization list
     * Returns a list of organization name strings
     */
    @Named("mapOrganizations")
    default List<UserOrganizationDto> mapOrganizations(List<UserOrganization> userOrganizations) {
        if (userOrganizations == null) {
            return null;
        }
        return userOrganizations.stream()
                .filter(ro -> ro != null && ro.getOrganization() != null && ro.getOrganization().getName() != null)
                .map(UserOrganizationDto::fromUserOrganization)
                .collect(Collectors.toList());
    }
}