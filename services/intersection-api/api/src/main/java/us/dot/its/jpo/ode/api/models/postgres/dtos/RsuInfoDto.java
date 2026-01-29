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
    Point geography;
    @NotNull
    Double milepost;
    @NotNull
    InetAddress ipv4Address;
    @NotNull
    @Size(max = 128)
    String serialNumber;
    @NotNull
    @Size(max = 128)
    String issScmsId;
    @NotNull
    @Size(max = 128)
    String primaryRoute;
    String modelName;
    String credentialNickname;
    String snmpCredentialNickname;
    String snmpProtocolNickname;
    List<Organization> rsuOrganizationOrganizations;
}