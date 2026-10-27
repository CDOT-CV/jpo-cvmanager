package us.dot.its.jpo.ode.api.models.devices;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.groups.Default;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import us.dot.its.jpo.ode.api.models.SimplePosition;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for {@link Rsu}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RsuInfoDto implements Serializable {
    // New RSUs require installed firmware. Existing records may have no firmware value.
    public interface Creation extends Default {}

    @JsonProperty("firmware_id")
    @NotNull(groups = Creation.class)
    @Positive
    Integer firmwareId;

    @JsonProperty("ip")
    @NotNull
    String ipv4Address;

    @JsonProperty("geo_position")
    @NotNull
    SimplePosition geoPosition;

    @JsonProperty("milepost")
    @NotNull
    Double milepost;

    @JsonProperty("primary_route")
    @NotNull
    @Size(max = 128)
    String primaryRoute;

    @JsonProperty("serial_number")
    @NotNull
    @Size(max = 128)
    String serialNumber;

    @JsonProperty("scms_id")
    @NotNull
    @Size(max = 128)
    String issScmsId;

    @JsonProperty("model")
    String model;

    @JsonProperty("ssh_credential_group")
    String sshCredentialGroup;

    @JsonProperty("snmp_credential_group")
    String snmpCredentialGroup;

    @JsonProperty("snmp_version_group")
    String snmpVersionGroup;

    @NotNull
    @Size(min = 1)
    @JsonProperty("organizations")
    List<String> organizations;

    @NotNull
    @JsonProperty("tim_deposit")
    Boolean timDeposit;

    @NotNull
    @JsonProperty("snmp_monitoring")
    Boolean snmpMonitoring;
}
