package us.dot.its.jpo.ode.api.mappers;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
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
     * Convert RsuInfoDto to Rsu entity
     * Note: Relationships (model, credentials, organizations) should be set in
     * service layer
     */
    @Mapping(source = "ipv4Address", target = "ipv4Address", qualifiedByName = "mapStringToInetAddress")
    @Mapping(source = "geoPosition", target = "geography", qualifiedByName = "mapSimplePositionToPoint")
    @Mapping(target = "id", ignore = true) // Auto-generated
    @Mapping(target = "model", ignore = true) // Set in service layer
    @Mapping(target = "credential", ignore = true) // Set in service layer
    @Mapping(target = "snmpCredential", ignore = true) // Set in service layer
    @Mapping(target = "snmpProtocol", ignore = true) // Set in service layer
    @Mapping(target = "rsuOrganizations", ignore = true) // Set in service layer
    @Mapping(target = "firmwareVersion", ignore = true)
    @Mapping(target = "targetFirmwareVersion", ignore = true)
    Rsu toEntity(RsuInfoDto dto);

    /**
     * Convert InetAddress to String IP address
     */
    @Named("mapInetAddressToString")
    default String mapInetAddressToString(InetAddress inetAddress) {
        if (inetAddress == null) {
            return null;
        }
        return inetAddress.getHostAddress();
    }

    /**
     * Convert String IP address to InetAddress
     */
    @Named("mapStringToInetAddress")
    default InetAddress mapStringToInetAddress(String ipAddress) {
        if (ipAddress == null) {
            return null;
        }
        try {
            return InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address: " + ipAddress, e);
        }
    }

    /**
     * Convert JTS Point to SimplePosition (latitude, longitude)
     */
    @Named("mapGeoPosition")
    default SimplePosition mapGeoPosition(Point geography) {
        if (geography == null) {
            return null;
        }
        return new SimplePosition(geography.getY(), geography.getX()); // Y = latitude, X = longitude
    }

    /**
     * Convert SimplePosition to JTS Point
     */
    @Named("mapSimplePositionToPoint")
    default Point mapSimplePositionToPoint(SimplePosition position) {
        if (position == null) {
            return null;
        }

        // Create GeometryFactory with SRID 4326 (WGS 84)
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

        // Create coordinate (longitude, latitude) - ORDER MATTERS!
        Coordinate coordinate = new Coordinate(position.longitude(), position.latitude());

        return geometryFactory.createPoint(coordinate);
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