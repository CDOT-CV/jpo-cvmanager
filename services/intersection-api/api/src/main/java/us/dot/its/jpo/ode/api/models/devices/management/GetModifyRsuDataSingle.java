package us.dot.its.jpo.ode.api.models.devices.management;

import lombok.Getter;
import lombok.Setter;
import us.dot.its.jpo.ode.api.models.postgres.dtos.RsuInfoDto;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GetModifyRsuDataSingle {
    @JsonProperty("rsu_data")
    private RsuInfoDto rsuData;
    @JsonProperty("allowed_selections")
    private ModifyRsuAllowedSelections allowedSelections;
}
