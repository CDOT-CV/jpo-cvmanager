package us.dot.its.jpo.ode.api.models.admin.intersection;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Response shape for GET /admin-intersection when intersection_id=all is requested.
 * allowed_selections is not included for list requests.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntersectionListResponse {
    @JsonProperty("intersection_data")
    private List<IntersectionData> intersectionData;
}
