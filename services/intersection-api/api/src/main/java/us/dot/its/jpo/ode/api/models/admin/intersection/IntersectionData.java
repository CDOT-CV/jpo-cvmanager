package us.dot.its.jpo.ode.api.models.admin.intersection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Represents a single intersection record as returned by GET /admin-intersection.
 *
 * When serialized with all fields null (not-found case), Jackson produces {}.
 * This is the intended behavior for the "single intersection not found" response shape.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntersectionData {
    @JsonProperty("intersection_id")
    private String intersectionId;

    @JsonProperty("ref_pt")
    private RefPt refPt;

    @JsonProperty("bbox")
    private Bbox bbox;

    @JsonProperty("intersection_name")
    private String intersectionName;

    @JsonProperty("origin_ip")
    private String originIp;

    @JsonProperty("organizations")
    private List<String> organizations;

    @JsonProperty("rsus")
    private List<String> rsus;
}
