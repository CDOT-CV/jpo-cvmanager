package us.dot.its.jpo.ode.api.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import us.dot.its.jpo.ode.api.models.credentials.RsuCredentialDTO;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RsuCredentialMapper {

    RsuCredentialDTO toDto(RsuCredential rsuCredential);
}
