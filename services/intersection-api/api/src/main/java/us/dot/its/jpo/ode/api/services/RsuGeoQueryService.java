package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

/**
 * Finds RSU IPv4 addresses for an organization inside a caller-supplied polygon.
 */
@Service
@RequiredArgsConstructor
public class RsuGeoQueryService {
    /**
     * Vendor value that means no manufacturer filter should be applied.
     */
    private static final String UNFILTERED_VENDOR = "Select Vendor";

    /**
     * Minimum positions in a PostGIS polygon ring, including the repeated closing position.
     */
    private static final int MIN_RING_POSITIONS = 4;

    private final RsuRepository rsuRepository;

    /**
     * Returns IPv4 host addresses of RSUs in {@code organization} whose geography lies inside {@code geometry}.
     *
     * @param organization organization name
     * @param geometry polygon ring of {@code [longitude, latitude]} pairs; must be closed, contain at least four
     *                 positions, and each position must contain finite longitude and latitude
     * @param vendor manufacturer name, or {@code null}, blank, or {@code "Select Vendor"} for no manufacturer filter
     * @return host addresses of matching RSUs; empty when the organization has no matches
     * @throws ResponseStatusException {@code 400} when {@code geometry} is missing, a point is incomplete or
     *                                 non-finite, the ring has fewer than four positions, or the ring is not closed
     */
    @Transactional(readOnly = true)
    public List<String> findRsuIps(String organization, List<List<Double>> geometry, String vendor) {
        String polygon = toPolygonWkt(geometry);
        String manufacturer = normalizeVendor(vendor);
        List<String> addresses = manufacturer == null
                ? rsuRepository.findIpv4AddressesInPolygon(organization, polygon)
                : rsuRepository.findIpv4AddressesInPolygonByManufacturer(organization, polygon, manufacturer);
        if (addresses == null || addresses.isEmpty()) {
            return List.of();
        }
        return addresses.stream().map(RsuGeoQueryService::hostAddress).toList();
    }

    /**
     * Builds a WKT polygon from {@code [longitude, latitude]} pairs without modifying {@code geometry}.
     *
     * @throws ResponseStatusException {@code 400} when the ring is missing, a point is incomplete or non-finite,
     *                                 the ring has fewer than four positions, or the ring is not closed
     */
    private static String toPolygonWkt(List<List<Double>> geometry) {
        if (geometry == null || geometry.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "geometry is required");
        }
        for (List<Double> point : geometry) {
            if (point == null || point.size() < 2 || point.get(0) == null || point.get(1) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each geometry point must contain longitude and latitude");
            }
            if (!Double.isFinite(point.get(0)) || !Double.isFinite(point.get(1))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each geometry coordinate must be a finite number");
            }
        }
        if (geometry.size() < MIN_RING_POSITIONS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Polygon ring must have at least 4 positions");
        }
        List<Double> first = geometry.getFirst();
        List<Double> last = geometry.getLast();
        if (Double.compare(first.get(0), last.get(0)) != 0 || Double.compare(first.get(1), last.get(1)) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Polygon ring must be closed");
        }

        StringBuilder wkt = new StringBuilder("POLYGON((");
        for (int i = 0; i < geometry.size(); i++) {
            List<Double> point = geometry.get(i);
            if (i > 0) {
                wkt.append(',');
            }
            wkt.append(point.get(0)).append(' ').append(point.get(1));
        }
        wkt.append("))");
        return wkt.toString();
    }

    /**
     * @return the trimmed manufacturer name, or {@code null} when no manufacturer filter applies
     */
    private static String normalizeVendor(String vendor) {
        if (vendor == null) {
            return null;
        }
        String trimmed = vendor.trim();
        if (trimmed.isEmpty() || UNFILTERED_VENDOR.equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    /**
     * @return the IPv4 host address for {@code ip}
     * @throws ResponseStatusException {@code 500} when {@code ip} is not a valid address
     */
    private static String hostAddress(String ip) {
        try {
            return InetAddress.getByName(ip).getHostAddress();
        } catch (UnknownHostException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid RSU IP address: " + ip, e);
        }
    }
}
