package us.dot.its.jpo.ode.api.controllers.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionListResponse;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionPatch;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionSingleResponse;
import us.dot.its.jpo.ode.api.models.keycloak.CvManagerAuthToken;
import us.dot.its.jpo.ode.api.services.AdminIntersectionService;
import us.dot.its.jpo.ode.api.services.PermissionService;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for admin intersection management.
 * Migrated from the Python Flask AdminIntersection resource at /admin-intersection.
 *
 * All authorization is handled in this layer (controller/auth), not in the service:
 *   - Role checks and intersection resource access are enforced via @PreAuthorize expressions.
 *   - Org restriction enforcement on PATCH (organizations_to_add/remove must be within the
 *     user's qualified orgs) is enforced in the method body via PermissionService.
 *   - AdminIntersectionService is responsible only for database operations.
 */
@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@RequestMapping("/admin-intersection")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin Intersection", description = "Manage traffic intersections and their organization/RSU relationships")
@ApiResponses(value = {
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error"),
})
public class AdminIntersectionController {

    private final AdminIntersectionService adminIntersectionService;
    private final PermissionService permissionService;

    /**
     * Returns intersection data filtered by the requesting user's organization context.
     *   - intersection_id="all": returns {intersection_data: [...]} (list, no allowed_selections)
     *   - specific intersection_id: returns {intersection_data: {...}, allowed_selections: {...}}
     *     If the intersection is not found, intersection_data is {} (empty object).
     * Authorization (outer check) runs before query parameter validation.
     */
    @Operation(
            summary = "Get intersection(s)",
            description = """
                    Returns all accessible intersections (intersection_id=all) or a single intersection
                    by number. Single requests also return allowed_selections for UI dropdown population.
                    Organization filtering is applied based on the requesting user's org context.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Missing or blank intersection_id parameter"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires USER role"),
    })
    @RequestMapping(method = RequestMethod.GET, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('USER')")
    public ResponseEntity<?> getIntersection(
            @RequestParam(name = "intersection_id")
            @NotBlank(message = "intersection_id must not be blank")
            String intersectionId,
            @RequestHeader(name = "Organization", required = false) String organization) {

        boolean isSuperUser = permissionService.isSuperUser();
        CvManagerAuthToken token = permissionService.getCvManagerAuthToken();
        List<String> userOrgs = token != null ? token.getQualifiedOrgList("USER") : Collections.emptyList();
        List<String> operatorOrgs = token != null ? token.getQualifiedOrgList("OPERATOR") : Collections.emptyList();

        if ("all".equals(intersectionId)) {
            IntersectionListResponse response = adminIntersectionService.getAllIntersections(
                    organization, isSuperUser, userOrgs);
            return ResponseEntity.ok(response);
        } else {
            IntersectionSingleResponse response = adminIntersectionService.getIntersection(
                    intersectionId, organization, isSuperUser, userOrgs, operatorOrgs);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * PATCH /admin-intersection
     *
     * Updates an intersection's properties and modifies its organization/RSU relationships.
     * Request body validation runs after the permission checks.
     *
     * Authorization (all enforced in this layer):
     *   1. @PreAuthorize: OPERATOR role AND access to the specific intersection.
     *   2. Method body: each org in organizations_to_add/remove must be in the user's
     *      qualified orgs (superusers exempt). Returns 403 if any org is not allowed.
     */
    @Operation(
            summary = "Update an intersection",
            description = """
                    Updates an existing intersection record and its organization/RSU associations.
                    Role check: OPERATOR required.
                    Intersection access check: user must have access to the specified intersection.
                    Org enforcement: organizations_to_add and organizations_to_remove must each be
                    within the user's qualified organizations (superusers exempt).
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Intersection successfully modified"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid required fields"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires OPERATOR role, intersection access, or org restriction violation"),
    })
    @RequestMapping(method = RequestMethod.PATCH, produces = "application/json", consumes = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || (@PermissionService.hasRole('OPERATOR') && @PermissionService.hasIntersection(#patch.origIntersectionId, 'OPERATOR'))")
    public ResponseEntity<Map<String, String>> patchIntersection(
            @RequestBody @Validated IntersectionPatch patch) {
        if (!permissionService.isSuperUser()) {
            CvManagerAuthToken token = permissionService.getCvManagerAuthToken();
            List<String> qualifiedOrgs = token != null
                    ? token.getQualifiedOrgList("OPERATOR")
                    : Collections.emptyList();
            Set<String> qualifiedOrgSet = new java.util.HashSet<>(qualifiedOrgs);
            boolean allOrgsAllowed = qualifiedOrgSet.containsAll(patch.getOrganizationsToAdd())
                    && qualifiedOrgSet.containsAll(patch.getOrganizationsToRemove());
            if (!allOrgsAllowed) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Not authorized to modify one or more of the specified organizations");
            }
        }
        String message = adminIntersectionService.patchIntersection(patch);
        return ResponseEntity.ok(Map.of("message", message));
    }

    /**
     * DELETE /admin-intersection?intersection_id={id}
     *
     * Removes an intersection and all its relationship records in dependency order.
     * Request parameter validation runs after the permission check.
     *
     * Authorization (enforced in this layer):
     *   1. @PreAuthorize: OPERATOR role AND access to the specific intersection.
     */
    @Operation(
            summary = "Delete an intersection",
            description = """
                    Removes an intersection and its intersection_organization and rsu_intersection records.
                    Role check: OPERATOR required.
                    Intersection access check: user must have access to the specified intersection.
                    Returns 404 if the intersection does not exist (fixed from Python behavior).
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Intersection successfully deleted"),
            @ApiResponse(responseCode = "400", description = "Missing or blank intersection_id parameter"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires OPERATOR role or no access to this intersection"),
            @ApiResponse(responseCode = "404", description = "Intersection not found"),
    })
    @RequestMapping(method = RequestMethod.DELETE, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || (@PermissionService.hasRole('OPERATOR') && @PermissionService.hasIntersection(#intersectionId, 'OPERATOR'))")
    public ResponseEntity<Map<String, String>> deleteIntersection(
            @RequestParam(name = "intersection_id")
            @NotBlank(message = "intersection_id must not be blank")
            String intersectionId) {
        String message = adminIntersectionService.deleteIntersection(intersectionId);
        return ResponseEntity.ok(Map.of("message", message));
    }
}
