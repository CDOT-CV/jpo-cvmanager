package us.dot.its.jpo.ode.api.controllers.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import us.dot.its.jpo.ode.api.services.AdminIntersectionService;

import java.util.Map;

/**
 * REST controller for admin intersection management.
 * Migrated from the Python Flask AdminIntersection resource at /admin-intersection.
 * Authorization notes:
 *   - @PreAuthorize on each method is the OUTER permission check (role only).
 *   - PATCH and DELETE also require an INNER permission check (OPERATOR + INTERSECTION resource type)
 *     performed inside AdminIntersectionService, after request body validation.
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

        if ("all".equals(intersectionId)) {
            IntersectionListResponse response = adminIntersectionService.getAllIntersections(organization);
            return ResponseEntity.ok(response);
        } else {
            IntersectionSingleResponse response = adminIntersectionService.getIntersection(intersectionId, organization);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * PATCH /admin-intersection
     *
     * Updates an intersection's properties and modifies its organization/RSU relationships.
     * Request body validation runs after the outer permission check.
     * The service performs the inner permission check (OPERATOR + INTERSECTION resource type)
     * and enforces org restrictions on organizations_to_add/remove.
     */
    @Operation(
            summary = "Update an intersection",
            description = """
                    Updates an existing intersection record and its organization/RSU associations.
                    Outer check: OPERATOR role required.
                    Inner check (in service): OPERATOR + INTERSECTION resource type access.
                    Org enforcement (in service): organizations_to_add and organizations_to_remove must
                    each be within the user's qualified organizations (superusers exempt).
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Intersection successfully modified"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid required fields"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires OPERATOR role, or org restriction violation"),
    })
    @RequestMapping(method = RequestMethod.PATCH, produces = "application/json", consumes = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('OPERATOR')")
    public ResponseEntity<Map<String, String>> patchIntersection(
            @RequestBody @Validated IntersectionPatch patch) {
        String message = adminIntersectionService.patchIntersection(patch);
        return ResponseEntity.ok(Map.of("message", message));
    }

    /**
     * DELETE /admin-intersection?intersection_id={id}
     *
     * Removes an intersection and all its relationship records in dependency order.
     * Request parameter validation runs after the outer permission check.
     * The service performs the inner permission check (OPERATOR + INTERSECTION resource type).
     */
    @Operation(
            summary = "Delete an intersection",
            description = """
                    Removes an intersection and its intersection_organization and rsu_intersection records.
                    Outer check: OPERATOR role required.
                    Inner check (in service): OPERATOR + INTERSECTION resource type access.
                    Returns 404 if the intersection does not exist (fixed from Python behavior).
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Intersection successfully deleted"),
            @ApiResponse(responseCode = "400", description = "Missing or blank intersection_id parameter"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires OPERATOR role, or no access to this intersection"),
            @ApiResponse(responseCode = "404", description = "Intersection not found"),
    })
    @RequestMapping(method = RequestMethod.DELETE, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('OPERATOR')")
    public ResponseEntity<Map<String, String>> deleteIntersection(
            @RequestParam(name = "intersection_id")
            @NotBlank(message = "intersection_id must not be blank")
            String intersectionId) {
        String message = adminIntersectionService.deleteIntersection(intersectionId);
        return ResponseEntity.ok(Map.of("message", message));
    }
}
