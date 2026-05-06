package us.dot.its.jpo.ode.api.models.admin.organization;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Request body for PATCH /organizations.
 */
@Schema(description = "Request body for updating an organization's properties and user/RSU/intersection associations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationPatch {

    @Schema(description = "Current organization name that identifies the record to update", example = "CDOT", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @JsonProperty("orig_name")
    private String origName;

    @Schema(description = "New organization name (may equal orig_name)", example = "CDOT", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @JsonProperty("name")
    private String name;

    @Schema(description = "New contact email for the organization; omit or null to clear", example = "contact@cdot.gov")
    @JsonProperty("email")
    private String email;

    @Schema(description = "User email + role pairs to add to this organization", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Valid
    @JsonProperty("users_to_add")
    private List<UserRoleAssignment> usersToAdd;

    @Schema(description = "User email + role pairs whose role should be changed within this organization", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Valid
    @JsonProperty("users_to_modify")
    private List<UserRoleAssignment> usersToModify;

    @Schema(description = "Email addresses of users to remove from this organization", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @JsonProperty("users_to_remove")
    private List<String> usersToRemove;

    @Schema(description = "RSU IPv4 addresses to associate with this organization", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @JsonProperty("rsus_to_add")
    private List<@Pattern(regexp = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$", message = "must be a valid IPv4 address") String> rsusToAdd;

    @Schema(description = "RSU IPv4 addresses to disassociate from this organization", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @JsonProperty("rsus_to_remove")
    private List<@Pattern(regexp = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$", message = "must be a valid IPv4 address") String> rsusToRemove;

    @Schema(description = "Intersection IDs (intersection_number) to associate with this organization", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @JsonProperty("intersections_to_add")
    private List<Integer> intersectionsToAdd;

    @Schema(description = "Intersection IDs (intersection_number) to disassociate from this organization", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @JsonProperty("intersections_to_remove")
    private List<Integer> intersectionsToRemove;

    @Schema(description = "When non-null, bulk-apply this TIM deposit setting to all RSUs currently in the organization")
    @JsonProperty("tim_deposit")
    private Boolean timDeposit;

    @Schema(description = "When non-null, bulk-apply this SNMP monitoring setting to all RSUs currently in the organization")
    @JsonProperty("snmp_monitoring")
    private Boolean snmpMonitoring;
}
