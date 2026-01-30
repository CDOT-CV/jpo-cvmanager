package us.dot.its.jpo.ode.api.models.devices.management;

import lombok.Getter;
import lombok.Setter;
import us.dot.its.jpo.ode.api.models.SimplePosition;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RsuPatch {
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

    @JsonProperty("model")
    String model;

    @JsonProperty("scms_id")
    @NotNull
    @Size(max = 128)
    String issScmsId;

    @JsonProperty("ssh_credential_group")
    String sshCredentialGroup;

    @JsonProperty("snmp_credential_group")
    String snmpCredentialGroup;

    @JsonProperty("snmp_version_group")
    String snmpVersionGroup;

    @JsonProperty("organizations_to_add")
    List<String> organizationsToAdd;

    @JsonProperty("organizations_to_remove")
    List<String> organizationsToRemove;
}
