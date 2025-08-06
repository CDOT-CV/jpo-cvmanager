package us.dot.its.jpo.ode.api.services;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import us.dot.its.jpo.ode.api.models.PrometheusResponse;

@Slf4j
@Service
public class PrometheusService {

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Cache<String, String> queryCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    @Value("${prometheus.url:http://localhost:9090}")
    private String prometheusUrl;

    @Value("${prometheus.timeout:30}")
    private int timeoutSeconds;

    @Value("${prometheus.aggregation.step.seconds:60}")
    private int aggregationStepSeconds;

    /**
     * Calculates the optimal step size for aggregation based on the time range.
     * Uses smaller steps for shorter time ranges and larger steps for longer
     * ranges.
     * 
     * @param startTime start time in milliseconds
     * @param endTime   end time in milliseconds
     * @return step size in seconds
     */
    private int calculateOptimalStepSize(long startTime, long endTime) {
        long durationSeconds = (endTime - startTime) / 1000;

        // For periods up to 6 hours, use 1-minute steps
        if (durationSeconds <= 21600) {
            return 60;
        }
        // For periods up to 24 hours, use 5-minute steps
        else if (durationSeconds <= 86400) {
            return 300;
        }
        // For longer periods, use 10-minute steps
        else {
            return 600;
        }
    }

    /**
     * Executes an instant query against Prometheus.
     * 
     * @param promQL the PromQL query string
     * @return the JSON response from Prometheus
     */
    public String query(String promQL) {
        return query(promQL, false);
    }

    /**
     * Executes an instant query against Prometheus with optional caching.
     * 
     * @param promQL   the PromQL query string
     * @param useCache whether to use the query cache
     * @return the JSON response from Prometheus
     */
    public String query(String promQL, boolean useCache) {
        if (useCache) {
            return queryCache.get(promQL, key -> executeQuery(key));
        }
        return executeQuery(promQL);
    }

    /**
     * Executes the actual HTTP request to Prometheus for an instant query.
     * 
     * @param promQL the PromQL query string
     * @return the JSON response from Prometheus
     * @throws RuntimeException if the query fails
     */
    private String executeQuery(String promQL) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(prometheusUrl)
                    .path("/api/v1/query")
                    .queryParam("query", promQL)
                    .build(false)
                    .toUri();

            String response = restTemplate.getForObject(uri, String.class);
            return response;
        } catch (Exception e) {
            log.error("Error querying Prometheus: {}", e.getMessage());
            throw new RuntimeException("Failed to query Prometheus", e);
        }
    }

    /**
     * Executes a range query against Prometheus.
     * 
     * @param promQL the PromQL query string
     * @param start  start timestamp in seconds
     * @param end    end timestamp in seconds
     * @param step   step interval in seconds
     * @return the JSON response from Prometheus
     */
    public String queryRange(String promQL, long start, long end, long step) {
        return queryRange(promQL, start, end, step, false);
    }

    /**
     * Executes a range query against Prometheus with optional caching.
     * 
     * @param promQL   the PromQL query string
     * @param start    start timestamp in seconds
     * @param end      end timestamp in seconds
     * @param step     step interval in seconds
     * @param useCache whether to use the query cache
     * @return the JSON response from Prometheus
     */
    public String queryRange(String promQL, long start, long end, long step, boolean useCache) {
        String cacheKey = String.format("%s_%d_%d_%d", promQL, start, end, step);

        if (useCache) {
            return queryCache.get(cacheKey, key -> executeRangeQuery(promQL, start, end, step));
        }
        return executeRangeQuery(promQL, start, end, step);
    }

    /**
     * Executes the actual HTTP request to Prometheus for a range query.
     * 
     * @param promQL the PromQL query string
     * @param start  start timestamp in seconds
     * @param end    end timestamp in seconds
     * @param step   step interval in seconds
     * @return the JSON response from Prometheus
     * @throws RuntimeException if the query fails
     */
    private String executeRangeQuery(String promQL, long start, long end, long step) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(prometheusUrl)
                    .path("/api/v1/query_range")
                    .queryParam("query", promQL)
                    .queryParam("start", start)
                    .queryParam("end", end)
                    .queryParam("step", step)
                    .build(false)
                    .toUri();

            String response = restTemplate.getForObject(uri, String.class);
            return response;
        } catch (Exception e) {
            log.error("Error querying Prometheus range: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to query Prometheus range", e);
        }
    }

    /**
     * Executes an optimized instant query for aggregated counts over a time range.
     * Uses the increase() function with proper time range calculation.
     * 
     * @param promQL the PromQL query string
     * @param start  start timestamp in milliseconds
     * @param end    end timestamp in milliseconds
     * @return the JSON response from Prometheus
     * @throws RuntimeException if the query fails
     */
    public String queryInstant(String promQL, long start, long end) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(prometheusUrl)
                    .path("/api/v1/query")
                    .queryParam("query", promQL)
                    .queryParam("time", end)
                    .build(false)
                    .toUri();

            String response = restTemplate.getForObject(uri, String.class);
            return response;
        } catch (Exception e) {
            log.error("Error querying Prometheus instant: {}", e.getMessage());
            throw new RuntimeException("Failed to query Prometheus instant", e);
        }
    }

    /**
     * Builds and executes a range query for RSU message counts with improved
     * accuracy.
     * Uses smaller aggregation periods and manually sums the results.
     * 
     * @param rsuIp     the IP address of the RSU
     * @param startTime start time in milliseconds
     * @param endTime   end time in milliseconds
     * @return the JSON response from Prometheus
     */
    public String getRsuMessageCounts(String rsuIp, String topic, long startTime, long endTime) {
        int stepSize = calculateOptimalStepSize(startTime, endTime);
        String promQL = String.format(
                "sum by (topic) (increase(kafka_produced_rsu_messages_total{rsu_ip=\"%s\", topic=\"%s\"}[%ds]))",
                rsuIp, topic, stepSize);
        return queryRange(promQL, startTime / 1000, endTime / 1000, stepSize);
    }

    /**
     * Builds and executes an optimized query for organization RSU counts.
     * 
     * @param rsuIps      comma-separated list of RSU IPs or regex pattern
     * @param messageType the message type to filter by
     * @param startTime   start time in milliseconds
     * @param endTime     end time in milliseconds
     * @return the JSON response from Prometheus
     */
    public String getOrganizationRsuCounts(String rsuIps, String messageType, long startTime, long endTime) {
        String promQL = String.format(
                "sum by (rsu_ip, topic) (increase(kafka_produced_rsu_messages_total{rsu_ip=~\"%s\"}[%ds]))",
                rsuIps, (endTime - startTime) / 1000);
        return queryInstant(promQL, startTime, endTime);
    }

    /**
     * Builds and executes a range query for organization RSU counts with improved
     * accuracy.
     * 
     * @param rsuIps      comma-separated list of RSU IPs or regex pattern
     * @param messageType the message type to filter by
     * @param startTime   start time in milliseconds
     * @param endTime     end time in milliseconds
     * @return the JSON response from Prometheus
     */
    public String getOrganizationRsuCountsAccurate(String rsuIps, String messageType, long startTime, long endTime) {
        int stepSize = calculateOptimalStepSize(startTime, endTime);
        String promQL = String.format(
                "sum by (rsu_ip, topic) (increase(kafka_produced_rsu_messages_total{rsu_ip=~\"%s\"}[%ds]))",
                rsuIps, stepSize);
        return queryRange(promQL, startTime / 1000, endTime / 1000, stepSize);
    }

    /**
     * Builds and executes an optimized query for organization RSU counts filtered
     * by topic.
     * 
     * @param rsuIps    comma-separated list of RSU IPs or regex pattern
     * @param topic     the specific topic to filter by
     * @param startTime start time in milliseconds
     * @param endTime   end time in milliseconds
     * @return the JSON response from Prometheus
     */
    public String getOrganizationRsuCountsByTopic(String rsuIps, String topic, long startTime, long endTime) {
        String promQL = String.format(
                "sum by (rsu_ip, topic) (increase(kafka_produced_rsu_messages_total{rsu_ip=~\"%s\", topic=\"%s\"}[%ds]))",
                rsuIps, topic, (endTime - startTime) / 1000);
        return queryInstant(promQL, startTime, endTime);
    }

    /**
     * Builds and executes a range query for organization RSU counts by topic with
     * improved accuracy.
     * 
     * @param rsuIps    comma-separated list of RSU IPs or regex pattern
     * @param topic     the specific topic to filter by
     * @param startTime start time in milliseconds
     * @param endTime   end time in milliseconds
     * @return the JSON response from Prometheus
     */
    public String getOrganizationRsuCountsByTopicAccurate(String rsuIps, String topic, long startTime, long endTime) {
        int stepSize = calculateOptimalStepSize(startTime, endTime);
        String promQL = String.format(
                "sum by (rsu_ip, topic) (increase(kafka_produced_rsu_messages_total{rsu_ip=~\"%s\", topic=\"%s\"}[%ds]))",
                rsuIps, topic, stepSize);
        return queryRange(promQL, startTime / 1000, endTime / 1000, stepSize);
    }

    /**
     * Builds and executes an optimized query to get available topics.
     * 
     * @param startTime start time in milliseconds
     * @param endTime   end time in milliseconds
     * @return the JSON response from Prometheus
     */
    public String getAvailableTopicCounts(long startTime, long endTime) {
        String promQL = String.format(
                "sum by (topic) (increase(kafka_produced_rsu_messages_total{}[%ds]))",
                (endTime - startTime) / 1000);
        return queryInstant(promQL, startTime, endTime);
    }

    /**
     * Builds and executes a range query to get available topics with improved
     * accuracy.
     * 
     * @param startTime start time in milliseconds
     * @param endTime   end time in milliseconds
     * @return the JSON response from Prometheus
     */
    public String getAvailableTopicCountsAccurate(long startTime, long endTime) {
        int stepSize = calculateOptimalStepSize(startTime, endTime);
        String promQL = String.format(
                "sum by (topic) (increase(kafka_produced_rsu_messages_total{}[%ds]))",
                stepSize);
        return queryRange(promQL, startTime / 1000, endTime / 1000, stepSize);
    }

    /**
     * Parses a Prometheus response and extracts metric values.
     * 
     * @param response the JSON response from Prometheus
     * @return list of metric objects, or empty list if parsing fails
     */
    public List<Map<String, Object>> parseMetricValues(String response) {
        try {
            PrometheusResponse prometheusResponse = objectMapper.readValue(response, PrometheusResponse.class);

            if ("success".equals(prometheusResponse.getStatus()) &&
                    prometheusResponse.getData() != null &&
                    prometheusResponse.getData().getResult() != null) {

                return prometheusResponse.getData().getResult().stream()
                        .map(result -> Map.of("metric", (Object) result.getMetric()))
                        .toList();
            }
            return List.of();
        } catch (Exception e) {
            log.error("Error parsing Prometheus response: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Processes a range query response and manually sums the values over time.
     * This provides more accurate counts by summing individual time series points
     * within the specified time frame.
     * 
     * @param response  the JSON response from Prometheus range query
     * @param startTime start time in milliseconds
     * @param endTime   end time in milliseconds
     * @return the aggregated result as a JSON string in the same format as instant
     *         queries
     */
    public String aggregateRangeResponse(String response, long startTime, long endTime) {
        try {
            PrometheusResponse prometheusResponse = objectMapper.readValue(response, PrometheusResponse.class);

            if (!"success".equals(prometheusResponse.getStatus())) {
                return response; // Return original response if not successful
            }

            var aggregatedResults = new ArrayList<Map<String, Object>>();

            if (prometheusResponse.getData() != null && prometheusResponse.getData().getResult() != null) {
                for (PrometheusResponse.PrometheusResult result : prometheusResponse.getData().getResult()) {
                    // Use Apache Commons Math for efficient aggregation with statistical validation
                    SummaryStatistics stats = new SummaryStatistics();

                    if (result.getValues() != null) {
                        for (List<Object> valuePair : result.getValues()) {
                            if (valuePair != null && valuePair.size() >= 2) {
                                Object timestampObj = valuePair.get(0);
                                Object valueObj = valuePair.get(1);

                                try {
                                    long timestamp = parseTimestamp(timestampObj);

                                    // Only include values within the specified time frame
                                    if (timestamp >= startTime / 1000 && timestamp <= endTime / 1000) {
                                        double value = parseValue(valueObj);
                                        if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                                            stats.addValue(value);
                                        }
                                    }
                                } catch (NumberFormatException e) {
                                    log.warn("Could not parse timestamp or value: timestamp={}, value={}", timestampObj,
                                            valueObj);
                                }
                            }
                        }
                    }

                    // Get the sum of all valid values within the time frame
                    double totalValue = stats.getSum();

                    // Create aggregated result in the same format as instant queries
                    // Use endTime as the timestamp
                    var aggregatedResult = Map.<String, Object>of(
                            "metric", result.getMetric(),
                            "value", List.of(endTime / 1000, totalValue));
                    aggregatedResults.add(aggregatedResult);
                }
            }

            // Create the aggregated response structure
            var aggregatedResponse = Map.<String, Object>of(
                    "status", "success",
                    "data", Map.<String, Object>of(
                            "resultType", "vector",
                            "result", aggregatedResults));

            return objectMapper.writeValueAsString(aggregatedResponse);
        } catch (Exception e) {
            log.error("Error aggregating range response: {}", e.getMessage());
            return response; // Return original response if aggregation fails
        }
    }

    /**
     * Helper method to parse timestamp objects from Prometheus response.
     * Handles different types that Prometheus might return (String, Number, etc.).
     * 
     * @param timestampObj the timestamp object from Prometheus response
     * @return parsed long timestamp value
     * @throws NumberFormatException if the timestamp cannot be parsed
     */
    private long parseTimestamp(Object timestampObj) throws NumberFormatException {
        if (timestampObj == null) {
            throw new NumberFormatException("Timestamp is null");
        }

        if (timestampObj instanceof Number) {
            return ((Number) timestampObj).longValue();
        } else if (timestampObj instanceof String) {
            return Long.parseLong((String) timestampObj);
        } else {
            return Long.parseLong(timestampObj.toString());
        }
    }

    /**
     * Helper method to parse value objects from Prometheus response.
     * Handles different types that Prometheus might return (String, Number, etc.).
     * 
     * @param valueObj the value object from Prometheus response
     * @return parsed double value
     * @throws NumberFormatException if the value cannot be parsed
     */
    private double parseValue(Object valueObj) throws NumberFormatException {
        if (valueObj == null) {
            throw new NumberFormatException("Value is null");
        }

        if (valueObj instanceof Number) {
            return ((Number) valueObj).doubleValue();
        } else if (valueObj instanceof String) {
            return Double.parseDouble((String) valueObj);
        } else {
            return Double.parseDouble(valueObj.toString());
        }
    }
}