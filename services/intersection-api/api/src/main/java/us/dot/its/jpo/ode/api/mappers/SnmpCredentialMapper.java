package us.dot.its.jpo.ode.api.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import us.dot.its.jpo.ode.api.models.credentials.SnmpCredentialDTO;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SnmpCredentialMapper {

    SnmpCredentialDTO toDto(SnmpCredential snmpCredential);
}