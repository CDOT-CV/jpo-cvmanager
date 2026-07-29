package us.dot.its.jpo.ode.api.services;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

import us.dot.its.jpo.ode.api.models.PrometheusResponse;

@Slf4j
@Service
public class PrometheusService {

    private static final String METRIC_NAME = "kafka_produced_rsu_messages_total";

    private final RestTemplate restTemplate;

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

    public PrometheusService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
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
            URI uri = UriComponentsBuilder.fromUriString(prometheusUrl)
                    .path("/api/v1/query")
                    .queryParam("query", promQL)
                    .build(false)
                    .toUri();

            return restTemplate.getForObject(uri, String.class);
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
            URI uri = UriComponentsBuilder.fromUriString(prometheusUrl)
                    .path("/api/v1/query_range")
                    .queryParam("query", promQL)
                    .queryParam("start", start)
                    .queryParam("end", end)
                    .queryParam("step", step)
                    .build(false)
                    .toUri();

            return restTemplate.getForObject(uri, String.class);
        } catch (Exception e) {
            log.error("Error querying Prometheus range: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to query Prometheus range", e);
        }
    }

    /**
     * Executes an instant query evaluated at the given end time.
     *
     * @param promQL    the PromQL query string
     * @param endMillis end timestamp in milliseconds (evaluation time)
     * @return the JSON response from Prometheus
     * @throws RuntimeException if the query fails
     */
    public String queryInstant(String promQL, long endMillis) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(prometheusUrl)
                    .path("/api/v1/query")
                    .queryParam("query", promQL)
                    .queryParam("time", endMillis / 1000)
                    .build(false)
                    .toUri();

            return restTemplate.getForObject(uri, String.class);
        } catch (Exception e) {
            log.error("Error querying Prometheus instant: {}", e.getMessage());
            throw new RuntimeException("Failed to query Prometheus instant", e);
        }
    }

    /**
     * Builds a sum_over_time(increase(...)) PromQL query that aggregates counter
     * increases across ephemeral hosts/pods over the requested window.
     *
     * @param metricSelector label selector body, e.g. {@code rsu_ip="1.2.3.4", topic="x"}
     * @param groupBy        comma-separated label names for {@code sum by (...)}
     * @param startTime      start time in milliseconds
     * @param endTime        end time in milliseconds
     * @return PromQL string
     */
    String buildSumOverTimeIncreaseQuery(String metricSelector, String groupBy, long startTime, long endTime) {
        long rangeSeconds = Math.max((endTime - startTime) / 1000, aggregationStepSeconds);
        int step = aggregationStepSeconds;
        String metric = metricSelector == null || metricSelector.isBlank()
                ? METRIC_NAME
                : String.format("%s{%s}", METRIC_NAME, metricSelector);
        return String.format(
                "sum by (%s) (sum_over_time(increase(%s[%ds])[%ds:%ds]))",
                groupBy, metric, step, rangeSeconds, step);
    }

    /**
     * RSU message counts for a single RSU IP and topic over a time range.
     * Uses sum_over_time(increase()) so increments from scaled/restarted ODE hosts
     * are included.
     *
     * @param rsuIp     the IP address of the RSU
     * @param topic     Kafka topic label
     * @param startTime start time in milliseconds
     * @param endTime   end time in milliseconds
     * @return the JSON response from Prometheus
     */
    public String getRsuMessageCounts(String rsuIp, String topic, long startTime, long endTime) {
        String selector = String.format("rsu_ip=\"%s\", topic=\"%s\"", rsuIp, topic);
        String promQL = buildSumOverTimeIncreaseQuery(selector, "topic", startTime, endTime);
        return queryInstant(promQL, endTime);
    }

    /**
     * Organization RSU counts filtered by topic over a time range.
     *
     * @param rsuIps    comma-separated list of RSU IPs or regex pattern
     * @param topic     the specific topic to filter by
     * @param startTime start time in milliseconds
     * @param endTime   end time in milliseconds
     * @return the JSON response from Prometheus
     */
    public String getOrganizationRsuCountsByTopic(String rsuIps, String topic, long startTime, long endTime) {
        String selector = String.format("rsu_ip=~\"%s\", topic=\"%s\"", rsuIps, topic);
        String promQL = buildSumOverTimeIncreaseQuery(selector, "rsu_ip, topic", startTime, endTime);
        return queryInstant(promQL, endTime);
    }

    /**
     * Available topic counts over a time range (used to resolve message type → topic).
     *
     * @param startTime start time in milliseconds
     * @param endTime   end time in milliseconds
     * @return the JSON response from Prometheus
     */
    public String getAvailableTopicCounts(long startTime, long endTime) {
        String promQL = buildSumOverTimeIncreaseQuery("", "topic", startTime, endTime);
        return queryInstant(promQL, endTime);
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
}
