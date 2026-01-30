package us.dot.its.jpo.ode.api.mappers;

import java.net.InetAddress;
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Point;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import us.dot.its.jpo.ode.api.models.SimplePosition;
import us.dot.its.jpo.ode.api.models.postgres.dtos.RsuInfoDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuOrganization;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RsuInfoMapper {

    /**
     * Convert Rsu entity to RsuInfoDto
     * MapStruct will automatically map fields with the same name
     */
    @Mapping(source = "ipv4Address", target = "ipv4Address", qualifiedByName = "mapInetAddressToString")
    @Mapping(source = "geography", target = "geoPosition", qualifiedByName = "mapGeoPosition")
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
        return inetAddress.getHostAddress();
    }

    /**
     * Combine model name and manufacturer name
     */
    @Named("mapGeoPosition")
    default SimplePosition mapLatitude(Point geography) {
        return new SimplePosition(geography.getY(), geography.getX());
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
}