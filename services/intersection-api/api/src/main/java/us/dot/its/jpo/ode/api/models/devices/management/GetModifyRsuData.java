package us.dot.its.jpo.ode.api.models.devices.management;

import lombok.Getter;
import lombok.Setter;
import us.dot.its.jpo.ode.api.models.postgres.derived.RsuDetailedInfo;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GetModifyRsuData {
    @JsonProperty("rsu_data")
    private List<RsuDetailedInfo> rsuData;
    @JsonProperty("allowed_selections")
    private ModifyRsuAllowedSelections allowedSelections;
}
