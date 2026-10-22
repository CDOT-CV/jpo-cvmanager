package us.dot.its.jpo.ode.api.models.postgres.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Flat properties block inside a GeoJSON Feature returned by the
 * /devices/rsus/info endpoint.
 * Includes protocol and ownership metadata for the webapp forwarding controls.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RsuGeoInfoPropertiesDto {

    @JsonProperty("rsu_id")
    private Integer rsuId;

    @JsonProperty("milepost")
    private Double milepost;

    @JsonProperty("ipv4_address")
    private String ipv4Address;

    @JsonProperty("serial_number")
    private String serialNumber;

    @JsonProperty("primary_route")
    private String primaryRoute;

    @JsonProperty("tim_deposit")
    private Boolean timDeposit;

    @JsonProperty("snmp_monitoring")
    private Boolean snmpMonitoring;

    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("snmp_protocol_code")
    private String snmpProtocolCode;

    // Ownership currently follows the SSH credential group, matching PermissionService.
    @JsonProperty("owner_organization_id")
    private Integer ownerOrganizationId;

    @JsonProperty("manufacturer_name")
    private String manufacturerName;
}
