package us.dot.its.jpo.ode.api.models.admin.intersection;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Bbox {
    @JsonProperty("latitude1")
    private Double latitude1;

    @JsonProperty("longitude1")
    private Double longitude1;

    @JsonProperty("latitude2")
    private Double latitude2;

    @JsonProperty("longitude2")
    private Double longitude2;
}
