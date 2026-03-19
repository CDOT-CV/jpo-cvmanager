package us.dot.its.jpo.ode.api.models.admin.intersection;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Request body for PATCH /admin-intersection.
 *
 * orig_intersection_id identifies the record to update (WHERE clause).
 * intersection_id is the new intersection number (may equal orig).
 * bbox, intersection_name, and origin_ip are optional; omitting them leaves those columns unchanged.
 * organizations_to_add/remove and rsus_to_add/remove must be present but may be empty lists.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntersectionPatch {
    @NotNull
    @JsonProperty("orig_intersection_id")
    private Integer origIntersectionId;

    @NotNull
    @JsonProperty("intersection_id")
    private Integer intersectionId;

    @NotNull
    @Valid
    @JsonProperty("ref_pt")
    private RefPt refPt;

    @Valid
    @JsonProperty("bbox")
    private Bbox bbox;

    @JsonProperty("intersection_name")
    private String intersectionName;

    @JsonProperty("origin_ip")
    private String originIp;

    @NotNull
    @JsonProperty("organizations_to_add")
    private List<String> organizationsToAdd;

    @NotNull
    @JsonProperty("organizations_to_remove")
    private List<String> organizationsToRemove;

    @NotNull
    @JsonProperty("rsus_to_add")
    private List<String> rsusToAdd;

    @NotNull
    @JsonProperty("rsus_to_remove")
    private List<String> rsusToRemove;
}
