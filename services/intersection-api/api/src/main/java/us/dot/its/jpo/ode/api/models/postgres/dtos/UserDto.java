package us.dot.its.jpo.ode.api.models.postgres.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Value;
import us.dot.its.jpo.ode.api.models.postgres.tables.User;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for {@link User}
 */
@Value
public class UserDto implements Serializable {

    @NotNull
    private Integer id;

    @Size(max = 128)
    @NotNull
    private String email;

    @Size(max = 128)
    private String firstName;

    @Size(max = 128)
    private String lastName;

    @NotNull
    private Boolean superUser;

    @JsonProperty("organizations")
    List<UserOrganizationDto> organizations;
}