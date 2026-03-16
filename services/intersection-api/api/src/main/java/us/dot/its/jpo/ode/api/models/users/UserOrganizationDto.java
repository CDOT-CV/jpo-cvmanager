package us.dot.its.jpo.ode.api.models.postgres.dtos;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserOrganizationDto {

    @NotNull
    private Integer id;

    @Size(max = 128)
    @NotNull
    private String organization;

    @Size(max = 128)
    @NotNull
    private String role;
}
