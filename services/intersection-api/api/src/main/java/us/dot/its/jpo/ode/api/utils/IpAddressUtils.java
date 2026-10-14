package us.dot.its.jpo.ode.api.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class IpAddressUtils {

    private static final String IPV4_PATTERN = "^\\d{1,3}(?:\\.\\d{1,3}){3}$";

    private IpAddressUtils() {
    }

    /**
     * Parses a dotted-quad IPv4 string. The pattern check runs first so that
     * hostnames are rejected without triggering a DNS lookup.
     *
     * @throws ResponseStatusException with 400 BAD_REQUEST if the value is not a valid IPv4 address
     */
    public static InetAddress toIpv4Address(String ip) {
        if (ip == null || !ip.matches(IPV4_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IP address must be IPv4");
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.getAddress().length != 4) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IP address must be IPv4");
            }
            return address;
        } catch (UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid IP address", exception);
        }
    }
}
