package us.dot.its.jpo.ode.api.models.postgres.derived;

import org.locationtech.jts.geom.Geometry;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Intermediate DTO for RSU query results with single organization
 * Used to gather data before aggregating organizations into a list
 */
@Getter
@Setter
@NoArgsConstructor
public class RsuDetailedInfoRow {
    private String ipv4Address;
    private Geometry geometry;
    private Float milepost;
    private String primaryRoute;
    private String serialNumber;
    private String issScmsId;
    private String model;
    private String sshCredential;
    private String snmpCredential;
    private String snmpVersion;
    private String orgName;

    // Explicit constructor for JPQL queries - must match exact order and types
    public RsuDetailedInfoRow(
            String ipv4Address,
            Geometry geometry,
            Float milepost,
            String primaryRoute,
            String serialNumber,
            String issScmsId,
            String model,
            String sshCredential,
            String snmpCredential,
            String snmpVersion,
            String orgName) {
        this.ipv4Address = ipv4Address;
        this.geometry = geometry;
        this.milepost = milepost;
        this.primaryRoute = primaryRoute;
        this.serialNumber = serialNumber;
        this.issScmsId = issScmsId;
        this.model = model;
        this.sshCredential = sshCredential;
        this.snmpCredential = snmpCredential;
        this.snmpVersion = snmpVersion;
        this.orgName = orgName;
    }
}
