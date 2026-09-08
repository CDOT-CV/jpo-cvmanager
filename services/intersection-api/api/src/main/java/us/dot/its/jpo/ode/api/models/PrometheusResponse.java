package us.dot.its.jpo.ode.api.models;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrometheusResponse {

    private static final String STATUS_SUCCESS = "success";

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

    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    public List<PrometheusResult> getResults() {
        if (data == null || data.getResult() == null) {
            return List.of();
        }
        return data.getResult();
    }

    /**
     * POJO representing the data section of a Prometheus response.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PrometheusResult {

        @JsonProperty("metric")
        private Map<String, String> metric;

        @JsonProperty("value")
        private List<Object> value;

        @JsonProperty("values")
        private List<List<Object>> values;

        public String getMetricLabel(String name) {
            if (metric == null || name == null) {
                return null;
            }
            return metric.get(name);
        }

        /**
         * Instant-query sample value. Prometheus encodes this as
         * {@code [timestamp, "value"]}.
         */
        public double getInstantValue() {
            if (value == null || value.size() < 2 || value.get(1) == null) {
                return 0.0;
            }

            Object sample = value.get(1);
            if (sample instanceof Number number) {
                return number.doubleValue();
            }

            try {
                return Double.parseDouble(sample.toString());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PrometheusStats {
        @JsonProperty("seriesFetched")
        private int seriesFetched;

        @JsonProperty("executionTimeMsec")
        private int executionTimeMsec;
    }
}