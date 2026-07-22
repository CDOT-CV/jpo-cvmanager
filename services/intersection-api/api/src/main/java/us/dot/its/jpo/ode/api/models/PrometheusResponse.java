package us.dot.its.jpo.ode.api.models;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POJO representing a Prometheus API response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrometheusResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("data")
    private PrometheusData data;

    @JsonProperty("errorType")
    private String errorType;

    @JsonProperty("error")
    private String error;

    @JsonProperty("warnings")
    private List<String> warnings;

    @JsonProperty("stats")
    private PrometheusStats stats;

    /**
     * POJO representing the data section of a Prometheus response.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrometheusData {

        @JsonProperty("resultType")
        private String resultType;

        @JsonProperty("result")
        private List<PrometheusResult> result;
    }

    /**
     * POJO representing a single result in a Prometheus response.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrometheusResult {

        @JsonProperty("metric")
        private Map<String, String> metric;

        @JsonProperty("value")
        private List<Object> value;

        @JsonProperty("values")
        private List<List<Object>> values;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrometheusStats {
        @JsonProperty("seriesFetched")
        private int seriesFetched;

        @JsonProperty("executionTimeMsec")
        private int executionTimeMsec;
    }
}