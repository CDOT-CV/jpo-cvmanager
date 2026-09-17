package us.dot.its.jpo.ode.api.models.organizations;

import java.io.Serializable;

import jakarta.validation.constraints.Size;
import lombok.Value;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;

/**
 * DTO for {@link Organization}
 */
@Value
public class OrganizationDto implements Serializable {

    Integer id;

    @Size(max = 128)
    String name;

    @Size(max = 128)
    String email;
}
