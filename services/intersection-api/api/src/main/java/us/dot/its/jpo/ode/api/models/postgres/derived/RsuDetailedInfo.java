package us.dot.its.jpo.ode.api.models.postgres.derived;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for RSU detailed information query results
 * Matches the Python get_rsu_data function output
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RsuDetailedInfo {
    private String ipv4Address;
    private Double longitude;
    private Double latitude;
    private Double milepost;
    private String primaryRoute;
    private String serialNumber;
    private String issScmsId;
    private String model; // Concatenated manufacturer + model name
    private String sshCredential;
    private String snmpCredential;
    private String snmpVersion;
    private String orgName;
}
