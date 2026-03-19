package us.dot.its.jpo.ode.api.models.admin.intersection;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response shape for GET /admin-intersection when a specific intersection_id is requested.
 *
 * When the intersection is found, intersection_data is populated.
 * When the intersection is not found, intersection_data is an empty IntersectionData (serializes as {}).
 * allowed_selections is always included for single-intersection requests.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntersectionSingleResponse {
    @JsonProperty("intersection_data")
    private IntersectionDto intersectionDto;

    @JsonProperty("allowed_selections")
    private AllowedSelections allowedSelections;
}
