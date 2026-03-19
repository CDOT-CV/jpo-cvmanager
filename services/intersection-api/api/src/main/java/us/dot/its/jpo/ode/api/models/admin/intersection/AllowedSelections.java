package us.dot.its.jpo.ode.api.models.admin.intersection;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * The set of organizations and RSUs the requesting user is allowed to assign to an intersection.
 * Superusers receive all orgs/RSUs; non-superusers receive only those within their qualified organizations.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AllowedSelections {
    @JsonProperty("organizations")
    private List<String> organizations;

    @JsonProperty("rsus")
    private List<String> rsus;
}
