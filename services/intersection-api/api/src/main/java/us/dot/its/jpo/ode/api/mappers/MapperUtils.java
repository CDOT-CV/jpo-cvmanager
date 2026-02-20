package us.dot.its.jpo.ode.api.mappers;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import us.dot.its.jpo.ode.api.models.SimplePosition;

public class MapperUtils {
    public static String mapInetAddressToString(InetAddress inetAddress) {
        if (inetAddress == null) {
            return null;
        }
        return inetAddress.getHostAddress();
    }

    public static InetAddress mapStringToInetAddress(String ipAddress) {
        if (ipAddress == null) {
            return null;
        }
        try {
            return InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address: " + ipAddress, e);
        }
    }

    public static SimplePosition mapPointToSimplePosition(Point geography) {
        if (geography == null) {
            return null;
        }
        return new SimplePosition(geography.getY(), geography.getX());
    }

    public static Point mapSimplePositionToPoint(SimplePosition position) {
        if (position == null) {
            return null;
        }

        // Create GeometryFactory with SRID 4326 (WGS 84)
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

        // Create coordinate (longitude, latitude) - ORDER MATTERS!
        Coordinate coordinate = new Coordinate(position.longitude(), position.latitude());

        return geometryFactory.createPoint(coordinate);
    }
}
