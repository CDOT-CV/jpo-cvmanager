package com.trihydro.rsuinfobridge.mapper;

import java.net.InetAddress;
import java.util.List;

import org.locationtech.jts.geom.Point;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import com.trihydro.rsuinfobridge.models.dtos.RsuDto;
import com.trihydro.rsuinfobridge.models.tables.Rsu;
import com.trihydro.rsuinfobridge.models.tables.RsuOption;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RsuDtoMapper {
    String AUTHENTICATION_PROTOCOL = "SHA";
    String PRIVACY_PROTOCOL = "AES";

    /**
     * Convert Rsu entity to RsuDto
     */
    @Mapping(source = "id", target = "id", qualifiedByName = "mapIdToString")
    @Mapping(source = "ipv4Address", target = "ipv4Address", qualifiedByName = "mapInetAddressToString")
    @Mapping(source = "snmpProtocol.protocolCode", target = "snmpProtocol")
    @Mapping(source = "snmpCredential.username", target = "snmpUsername")
    @Mapping(source = "snmpCredential.password", target = "snmpPassword")
    @Mapping(target = "authenticationProtocol", constant = AUTHENTICATION_PROTOCOL)
    @Mapping(target = "privacyProtocol", constant = PRIVACY_PROTOCOL)
    @Mapping(source = "geography", target = "latitude", qualifiedByName = "mapLatitude")
    @Mapping(source = "geography", target = "longitude", qualifiedByName = "mapLongitude")
    @Mapping(source = "rsuOption", target = "timDepositEnabled", qualifiedByName = "mapTimDepositEnabled")
    RsuDto toDto(Rsu rsu);

    List<RsuDto> toDtoList(List<Rsu> rsus);

    @Named("mapIdToString")
    default String mapIdToString(Integer id) {
        if (id == null) {
            return null;
        }
        return id.toString();
    }

    @Named("mapInetAddressToString")
    default String mapInetAddressToString(InetAddress inetAddress) {
        if (inetAddress == null) {
            return null;
        }
        return inetAddress.getHostAddress();
    }

    @Named("mapLatitude")
    default double mapLatitude(Point geography) {
        if (geography == null) {
            return 0.0;
        }
        return geography.getY();
    }

    @Named("mapLongitude")
    default double mapLongitude(Point geography) {
        if (geography == null) {
            return 0.0;
        }
        return geography.getX();
    }

    @Named("mapTimDepositEnabled")
    default boolean mapTimDepositEnabled(RsuOption rsuOption) {
        if (rsuOption == null) {
            return false;
        }
        return Boolean.TRUE.equals(rsuOption.getTimDeposit());
    }
}
