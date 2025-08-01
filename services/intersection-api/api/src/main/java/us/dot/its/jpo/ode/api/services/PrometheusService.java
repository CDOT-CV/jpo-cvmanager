package us.dot.its.jpo.ode.api.services;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

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

            log.debug("Querying Prometheus: {}", promQL);
            String response = restTemplate.getForObject(uri, String.class);
            log.debug("Prometheus response received");
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

            log.debug("Prometheus range query: {} from {} to {} step {}", promQL, start, end, step);
            String response = restTemplate.getForObject(uri, String.class);
            log.debug("Prometheus range response received");
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

            log.debug("Querying Prometheus instant: {} at time {}", promQL, end);
            String response = restTemplate.getForObject(uri, String.class);
            log.debug("Prometheus instant response received");
            return response;
        } catch (Exception e) {
            log.error("Error querying Prometheus instant: {}", e.getMessage());
            throw new RuntimeException("Failed to query Prometheus instant", e);
        }
    }

    /**
     * Builds and executes an optimized query for RSU message counts.
     * Uses the increase() function with proper time range to get aggregated counts.
     * 
     * @param rsuIp     the IP address of the RSU
     * @param startTime start time in milliseconds
     * @param endTime   end time in milliseconds
     * @return the JSON response from Prometheus
     */
    public String getRsuMessageCounts(String rsuIp, long startTime, long endTime) {
        String promQL = String.format(
                "sum by (topic) (increase(kafka_produced_rsu_messages_total{rsu_ip=\"%s\"}[%ds]))",
                rsuIp, (endTime - startTime) / 1000);
        return queryInstant(promQL, startTime, endTime);
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
     * Parses a Prometheus response and extracts metric values.
     * 
     * @param response the JSON response from Prometheus
     * @return list of metric objects, or empty list if parsing fails
     */
    public List<Map<String, Object>> parseMetricValues(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            if (root.path("status").asText().equals("success")) {
                JsonNode results = root.path("data").path("result");
                return results.findValues("metric").stream()
                        .map(metric -> Map.of("metric", (Object) metric))
                        .toList();
            }
            return List.of();
        } catch (Exception e) {
            log.error("Error parsing Prometheus response: {}", e.getMessage());
            return List.of();
        }
    }
}