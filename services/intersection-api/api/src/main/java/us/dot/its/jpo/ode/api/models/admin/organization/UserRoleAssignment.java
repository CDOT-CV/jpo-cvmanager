package us.dot.its.jpo.ode.api.models.admin.organization;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "An email + role pair used for adding or modifying a user's membership in an organization")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleAssignment {

    @Schema(description = "User email address", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @JsonProperty("email")
    private String email;

    @Schema(description = "Role name to assign (e.g. ADMIN, OPERATOR, USER)", example = "OPERATOR", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @JsonProperty("role")
    private String role;
}
