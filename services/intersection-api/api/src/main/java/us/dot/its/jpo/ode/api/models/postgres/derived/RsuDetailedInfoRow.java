package us.dot.its.jpo.ode.api.models.postgres.derived;

import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class RsuDetailedInfoRow {
    private String ipv4Address;
    private Double latitude;
    private Double longitude;
    private Double milepost;
    private String primaryRoute;
    private String serialNumber;
    private String issScmsId;
    private String model;
    private String sshCredential;
    private String snmpCredential;
    private String snmpVersion;
    private String orgName;
}
