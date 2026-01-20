package us.dot.its.jpo.ode.api.models.postgres.derived;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

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
    @JsonProperty("ip")
    private String ip;
    @JsonProperty("geo_position")
    private GeoPosition geoPosition;
    @JsonProperty("milepost")
    private Double milepost;
    @JsonProperty("primary_route")
    private String primaryRoute;
    @JsonProperty("serial_number")
    private String serialNumber;
    @JsonProperty("scms_id")
    private String scmsId;
    @JsonProperty("model")
    private String model;
    @JsonProperty("ssh_credential_group")
    private String sshCredentialGroup;
    @JsonProperty("snmp_credential_group")
    private String snmpCredentialGroup;
    @JsonProperty("snmp_version_group")
    private String snmpVersionGroup;
    @JsonProperty("organizations")
    private List<String> organizations;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoPosition {
        @JsonProperty("latitude")
        private Double latitude;
        @JsonProperty("longitude")
        private Double longitude;
    }
}