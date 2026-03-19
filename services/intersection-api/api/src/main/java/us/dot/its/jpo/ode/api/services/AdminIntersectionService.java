package us.dot.its.jpo.ode.api.services;

import org.springframework.stereotype.Service;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionListResponse;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionPatch;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionSingleResponse;

/**
 * Service stub for admin intersection management.
 *
 * This service is responsible only for database operations. All authorization
 * (role checks, intersection resource access, and org restriction enforcement)
 * is handled by AdminIntersectionController before this service is called.
 *
 * TODO: Implement each method. The service is responsible for:
 *   - All database reads and writes described in admin_intersection_migration_spec.md
 *   - Wrapping patch/delete writes in a single transaction (fixes known issue #1)
 *   - Returning 404 (throw EntityNotFoundException) when deleting a nonexistent intersection (fixes known issue #2)
 */
@Service
public class AdminIntersectionService {

    /**
     * Returns a single intersection by intersection_number, plus allowed_selections for UI dropdowns.
     * Applies org filtering based on the requesting user's context (see spec: Organization Filtering).
     *
     * @param intersectionId the intersection_number to look up
     * @param organization   the scoped organization from the request header (may be null)
     * @return response containing intersection_data (empty IntersectionData if not found) and allowed_selections
     */
    public IntersectionSingleResponse getIntersection(String intersectionId, String organization) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Returns all intersections accessible to the requesting user, filtered by organization context.
     * Does NOT include allowed_selections.
     *
     * @param organization the scoped organization from the request header (may be null)
     * @return response containing intersection_data as a list (may be empty)
     */
    public IntersectionListResponse getAllIntersections(String organization) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Updates an intersection's properties and modifies its org/RSU relationships.
     * All authorization has already been enforced by the controller before this is called.
     *
     * @param patch the patch request body
     * @return success message
     */
    public String patchIntersection(IntersectionPatch patch) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Deletes an intersection and all its relationship records.
     * All authorization has already been enforced by the controller before this is called.
     *
     * Must perform:
     *   1. Delete in order: intersection_organization → rsu_intersection → intersections
     *   2. Throw EntityNotFoundException if the intersection does not exist (fixes known issue #2)
     *
     * @param intersectionId the intersection_number to delete
     * @return success message
     */
    public String deleteIntersection(String intersectionId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
