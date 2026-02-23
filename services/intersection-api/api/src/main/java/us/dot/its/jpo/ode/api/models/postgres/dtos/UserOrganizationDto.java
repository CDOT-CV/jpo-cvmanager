package us.dot.its.jpo.ode.api.models.postgres.dtos;

import lombok.Getter;
import lombok.Setter;
import us.dot.its.jpo.ode.api.models.postgres.tables.UserOrganization;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserOrganizationDto {
    @Size(max = 128)
    @NotNull
    private String organization;

    @Size(max = 128)
    @NotNull
    private String role;

    public static UserOrganizationDto fromUserOrganization(UserOrganization userOrganization) {
        if (userOrganization == null || userOrganization.getOrganization() == null) {
            return null;
        }
        return new UserOrganizationDto(userOrganization.getOrganization().getName(),
                userOrganization.getRole().getName());
    }
}
