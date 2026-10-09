package us.dot.its.jpo.ode.api.models.geo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Request body for the CV Manager polygon query over processed BSM and PSM data. */
public record ProcessedGeoMessageRequest(
        List<List<Double>> geometry,
        String start,
        String end,
        @JsonProperty("msg_type") String msgType) {
}
