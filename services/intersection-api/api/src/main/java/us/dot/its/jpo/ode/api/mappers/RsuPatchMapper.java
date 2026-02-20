package us.dot.its.jpo.ode.api.mappers;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import us.dot.its.jpo.ode.api.models.devices.management.RsuPatch;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = { MapperUtils.class })
public interface RsuPatchMapper {

    /**
     * Update existing Rsu entity with non-null values from RsuPatch
     * Null values in the patch are ignored (existing values preserved)
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "ipv4Address", target = "ipv4Address")
    @Mapping(source = "geoPosition", target = "geography")
    @Mapping(target = "model", ignore = true) // Set in service layer
    @Mapping(target = "credential", ignore = true) // Set in service layer
    @Mapping(target = "snmpCredential", ignore = true) // Set in service layer
    @Mapping(target = "snmpProtocol", ignore = true) // Set in service layer
    @Mapping(target = "rsuOrganizations", ignore = true) // Set in service layer
    @Mapping(target = "id", ignore = true) // Never update ID
    @Mapping(target = "firmwareVersion", ignore = true)
    @Mapping(target = "targetFirmwareVersion", ignore = true)
    void updateRsuFromPatch(RsuPatch patch, @MappingTarget Rsu rsu);

    /**
     * Convert RsuPatch to new Rsu entity (for create operations)
     */
    @Mapping(source = "ipv4Address", target = "ipv4Address")
    @Mapping(source = "geoPosition", target = "geography")
    @Mapping(target = "model", ignore = true) // Set in service layer
    @Mapping(target = "credential", ignore = true) // Set in service layer
    @Mapping(target = "snmpCredential", ignore = true) // Set in service layer
    @Mapping(target = "snmpProtocol", ignore = true) // Set in service layer
    @Mapping(target = "rsuOrganizations", ignore = true) // Set in service layer
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "firmwareVersion", ignore = true)
    @Mapping(target = "targetFirmwareVersion", ignore = true)
    Rsu toRsu(RsuPatch rsuPatch);
}