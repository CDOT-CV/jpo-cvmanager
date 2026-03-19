package us.dot.its.jpo.ode.api.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.Intersection;
import us.dot.its.jpo.ode.api.models.postgres.tables.IntersectionOrganization;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MapStruct mapper for converting Intersection entities to IntersectionData DTOs.
 *
 * The {@code rsus} field is intentionally excluded (mapped to ignore) because RSU IPs
 * are fetched via a separate query in the service and set manually after mapping.
 * This avoids loading the rsuIntersections lazy collection during entity-to-DTO conversion.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = { GeometryMapper.class, INetMapper.class })
public interface IntersectionMapper {

    @Mapping(source = "intersectionNumber", target = "intersectionId")
    @Mapping(source = "refPt", target = "refPt")
    @Mapping(source = "bbox", target = "bbox")
    @Mapping(source = "intersectionName", target = "intersectionName")
    @Mapping(source = "originIp", target = "originIp")
    @Mapping(source = "intersectionOrganizations", target = "organizations",
            qualifiedByName = "mapOrgNames")
    @Mapping(target = "rsus", ignore = true)
    IntersectionDto toDto(Intersection intersection);

    @Named("mapOrgNames")
    default List<String> mapOrgNames(List<IntersectionOrganization> intersectionOrganizations) {
        if (intersectionOrganizations == null) {
            return Collections.emptyList();
        }
        return intersectionOrganizations.stream()
                .filter(io -> io.getOrganization() != null && io.getOrganization().getName() != null)
                .map(io -> io.getOrganization().getName())
                .collect(Collectors.toList());
    }
}
