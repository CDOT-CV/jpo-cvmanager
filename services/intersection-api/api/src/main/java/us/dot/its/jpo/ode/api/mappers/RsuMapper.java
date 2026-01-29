package us.dot.its.jpo.ode.api.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import us.dot.its.jpo.ode.api.models.postgres.dtos.RsuInfoDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RsuMapper {
    
    /**
     * Convert Rsu entity to RsuInfoDto
     * MapStruct will automatically map fields with the same name
     */
    RsuInfoDto toDto(Rsu rsu);
    
    /**
     * Convert RsuInfoDto to Rsu entity
     */
    Rsu toEntity(RsuInfoDto dto);
}