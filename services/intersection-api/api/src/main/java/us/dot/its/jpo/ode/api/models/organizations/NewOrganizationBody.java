package us.dot.its.jpo.ode.api.models.organizations;

import java.io.Serializable;

import jakarta.validation.constraints.Size;
import lombok.Value;

@Value
public class NewOrganizationBody implements Serializable {

    @Size(max = 128)
    String name;

    @Size(max = 128)
    String email;
}
