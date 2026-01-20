package us.dot.its.jpo.ode.api.utils;

import us.dot.its.jpo.ode.api.models.postgres.derived.RsuDetailedInfo;
import us.dot.its.jpo.ode.api.models.postgres.derived.RsuDetailedInfoRow;

import java.util.*;

/**
 * Utility class for aggregating RSU data rows into DTOs with organization lists
 */
public class RsuAggregationUtil {

    /**
     * Aggregate multiple RSU info rows (one per organization) into a single object
     * with organizations as a list.
     * Matches the Python logic in get_rsu_data() that builds rsu_dict
     * 
     * @param rows List of RSU detail rows from database query
     * @return Map of IP address to aggregated RsuDetailedInfo
     */
    public static Map<String, RsuDetailedInfo> aggregateRsuRows(List<RsuDetailedInfoRow> rows) {
        Map<String, RsuDetailedInfo> rsuMap = new LinkedHashMap<>();

        for (RsuDetailedInfoRow row : rows) {
            String ip = row.getIpv4Address();

            // Get or create the RSU info object
            RsuDetailedInfo rsuInfo = rsuMap.get(ip);
            if (rsuInfo == null) {
                rsuInfo = new RsuDetailedInfo();
                rsuInfo.setIp(ip);

                // Set geo_position
                RsuDetailedInfo.GeoPosition geoPosition = new RsuDetailedInfo.GeoPosition();
                geoPosition.setLatitude(row.getGeometry().getCoordinate().y);
                geoPosition.setLongitude(row.getGeometry().getCoordinate().x);
                rsuInfo.setGeoPosition(geoPosition);

                rsuInfo.setMilepost(row.getMilepost());
                rsuInfo.setPrimaryRoute(row.getPrimaryRoute());
                rsuInfo.setSerialNumber(row.getSerialNumber());
                rsuInfo.setScmsId(row.getIssScmsId());
                rsuInfo.setModel(row.getModel());
                rsuInfo.setSshCredentialGroup(row.getSshCredential());
                rsuInfo.setSnmpCredentialGroup(row.getSnmpCredential());
                rsuInfo.setSnmpVersionGroup(row.getSnmpVersion());
                rsuInfo.setOrganizations(new ArrayList<>());

                rsuMap.put(ip, rsuInfo);
            }

            // Add the organization name to the list
            rsuInfo.getOrganizations().add(row.getOrgName());
        }

        return rsuMap;
    }

    /**
     * Convert aggregated map to a list of RSU details
     * 
     * @param rows List of RSU detail rows from database query
     * @return List of aggregated RsuDetailedInfo objects
     */
    public static List<RsuDetailedInfo> aggregateRsuRowsToList(List<RsuDetailedInfoRow> rows) {
        return new ArrayList<>(aggregateRsuRows(rows).values());
    }

    /**
     * Get single RSU detail from rows, or null if not found
     * Matches Python logic: return first item if single RSU requested, empty object
     * if not found
     * 
     * @param rows List of RSU detail rows from database query
     * @return Single RsuDetailedInfo or null if not found
     */
    public static RsuDetailedInfo aggregateRsuRowsToSingle(List<RsuDetailedInfoRow> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, RsuDetailedInfo> rsuMap = aggregateRsuRows(rows);
        return rsuMap.values().iterator().next();
    }
}
