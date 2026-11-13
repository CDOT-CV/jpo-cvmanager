package us.dot.its.jpo.ode.api.models.organizations;

import java.io.Serializable;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

@Value
public class NewOrganizationBody implements Serializable {

    @Schema(description = "Name of the organization", example = "Example Organization")
    @NotNull
    @Size(max = 128)
    String name;

    @Schema(description = "Organization owner/management email", example = "example@organization.com")
    @NotNull
    @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "Must be a valid email address")
    @Size(max = 128)
    String email;
}
