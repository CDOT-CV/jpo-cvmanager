package us.dot.its.jpo.ode.api.models.devices.management;

import lombok.Getter;
import lombok.Setter;
import us.dot.its.jpo.ode.api.models.devices.rsu.RsuInfo;
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
    private RsuInfo rsuData;
    @JsonProperty("allowed_selections")
    private List<ModifyRsuAllowedSelections> allowedSelections;
}
