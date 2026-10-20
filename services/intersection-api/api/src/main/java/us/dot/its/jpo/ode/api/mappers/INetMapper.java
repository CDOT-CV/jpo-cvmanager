package us.dot.its.jpo.ode.api.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import us.dot.its.jpo.ode.api.services.RsuManagementService;

import java.net.Inet4Address;

/**
 * MapStruct mapper for converting between Inet4Address and String.
 * This mapper is automatically used by other mappers when they need to convert
 * IP addresses.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface INetMapper {

    /**
     * Converts Inet4Address to String representation (e.g., "192.168.1.1")
     */
    default String mapInet4AddressToString(Inet4Address Inet4Address) {
        if (Inet4Address == null) {
            return null;
        }
        return Inet4Address.getHostAddress();
    }

    /**
     * Converts String IP address to Inet4Address object
     * 
     * @throws IllegalArgumentException if the IP address is invalid
     */
    default Inet4Address mapStringToInet4Address(String ipAddress) {
        if (ipAddress == null) {
            return null;
        }
        return RsuManagementService.toIpv4Address(ipAddress);
    }
}