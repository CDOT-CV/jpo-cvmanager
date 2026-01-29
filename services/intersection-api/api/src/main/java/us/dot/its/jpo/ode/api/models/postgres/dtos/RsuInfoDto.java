package us.dot.its.jpo.ode.api.models.postgres.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Value;
import org.locationtech.jts.geom.Point;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.List;

/**
 * DTO for {@link Rsu}
 */
@Value
public class RsuInfoDto implements Serializable {
    @NotNull
    String ipv4Address;
    @NotNull
    Double longitude;
    @NotNull
    Double latitude;
    @NotNull
    Double milepost;
    @NotNull
    @Size(max = 128)
    String primaryRoute;
    @NotNull
    @Size(max = 128)
    String serialNumber;
    @NotNull
    @Size(max = 128)
    String scmsId;
    String model;
    String sshCredentialGroup;
    String snmpCredentialGroup;
    String snmpVersionGroup;
    List<String> organizations;
}