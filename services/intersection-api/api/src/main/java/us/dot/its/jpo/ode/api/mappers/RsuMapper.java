package us.dot.its.jpo.ode.api.mappers;

import java.net.InetAddress;
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Point;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import us.dot.its.jpo.ode.api.models.postgres.dtos.RsuInfoDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuOrganization;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RsuMapper {
    
    /**
     * Convert Rsu entity to RsuInfoDto
     * MapStruct will automatically map fields with the same name
     */
    @Mapping(source = "ipv4Address", target = "ipv4Address", qualifiedByName = "mapInetAddressToString")
    @Mapping(source = "issScmsId", target = "scmsId")
    @Mapping(source = "geography", target = "latitude", qualifiedByName = "mapLatitude")
    @Mapping(source = "geography", target = "longitude", qualifiedByName = "mapLongitude")
    @Mapping(target = "model", expression = "java(mapModelNames(rsu.getModel()))")
    @Mapping(source = "credential.nickname", target = "sshCredentialGroup")
    @Mapping(source = "snmpCredential.nickname", target = "snmpCredentialGroup")
    @Mapping(source = "snmpProtocol.nickname", target = "snmpVersionGroup")
    @Mapping(source = "rsuOrganizations", target = "organizations", qualifiedByName = "mapOrganizationNames")
    RsuInfoDto toDto(Rsu rsu);

    /**
     * Combine model name and manufacturer name
     */
    @Named("mapInetAddressToString")
    default String mapInetAddressToString(InetAddress inetAddress) {
        return String.valueOf(inetAddress.getAddress()[0]);
    }

    /**
     * Combine model name and manufacturer name
     */
    @Named("mapLatitude")
    default Double mapLatitude(Point geography) {
        return geography.getY();
    }

    /**
     * Combine model name and manufacturer name
     */
    @Named("mapLongitude")
    default Double mapLongitude(Point geography) {
        return geography.getX();
    }

    /**
     * Combine model name and manufacturer name
     */
    default String mapModelNames(us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel rsuModel) {
        if (rsuModel == null || rsuModel.getManufacturer() == null) {
            return rsuModel != null ? rsuModel.getName() : null;
        }
        return String.format("%s %s", rsuModel.getManufacturer().getName(), rsuModel.getName());
    }

    /**
     * Extract organization names from RsuOrganization list
     */
    @Named("mapOrganizationNames")
    default List<String> mapOrganizationNames(List<RsuOrganization> rsuOrganizations) {
        if (rsuOrganizations == null) {
            return null;
        }
        return rsuOrganizations.stream()
                .map(ro -> ro.getOrganization().getName())
                .collect(Collectors.toList());
    }
    
    /**
     * Convert RsuInfoDto to Rsu entity
     */
    @Mapping(source = "ipv4Address", target = "ipv4Address", qualifiedByName = "mapStringToInetAddress")
    @Mapping(source = "model", target = "model", qualifiedByName = "mapStringToRsuModel")
    @Mapping(source = "organizations", target = "rsuOrganizations", qualifiedByName = "mapOrganizationNamesReverse")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "geography", ignore = true)
    @Mapping(target = "issScmsId", ignore = true)
    @Mapping(target = "credential", ignore = true)
    @Mapping(target = "snmpCredential", ignore = true)
    @Mapping(target = "snmpProtocol", ignore = true)
    @Mapping(target = "firmwareVersion", ignore = true)
    @Mapping(target = "targetFirmwareVersion", ignore = true)
    Rsu toEntity(RsuInfoDto dto);

    /**
     * Map String to InetAddress for reverse mapping (best-effort, returns null on
     * error)
     */
    @Named("mapStringToInetAddress")
    default InetAddress mapStringToInetAddress(String addr) {
        if (addr == null) {
            return null;
        }
        try {
            return InetAddress.getByName(addr);
        } catch (Exception e) {
            // swallow exception and return null so MapStruct can proceed
            return null;
        }
    }

    /**
     * Map model name string back to RsuModel (manufacturer not known when coming
     * from DTO)
     */
    @Named("mapStringToRsuModel")
    default us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel mapStringToRsuModel(String model) {
        if (model == null) {
            return null;
        }
        us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel rm = new us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel();
        rm.setName(model);
        // manufacturer information is not available from the DTO string; leave null
        return rm;
    }

    /**
     * Map list of organization names back to RsuOrganization list
     */
    @Named("mapOrganizationNamesReverse")
    default List<RsuOrganization> mapOrganizationNamesReverse(List<String> orgNames) {
        if (orgNames == null) {
            return null;
        }
        return orgNames.stream()
                .map(name -> {
                    RsuOrganization ro = new RsuOrganization();
                    us.dot.its.jpo.ode.api.models.postgres.tables.Organization org = new us.dot.its.jpo.ode.api.models.postgres.tables.Organization();
                    org.setName(name);
                    ro.setOrganization(org);
                    return ro;
                })
                .collect(Collectors.toList());
    }
}