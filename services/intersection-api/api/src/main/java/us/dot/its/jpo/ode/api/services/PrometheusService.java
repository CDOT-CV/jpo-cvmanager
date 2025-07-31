package us.dot.its.jpo.ode.api.services;

import java.net.URI;
import java.time.Duration;
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

    // Cache for query results to reduce load on Prometheus
    private final Cache<String, String> queryCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    @Value("${prometheus.url:http://localhost:9090}")
    private String prometheusUrl;

    @Value("${prometheus.timeout:30}")
    private int timeoutSeconds;

    /**
     * Execute an instant query with better error handling
     */
    public String query(String promQL) {
        return query(promQL, false);
    }

    public String query(String promQL, boolean useCache) {
        if (useCache) {
            return queryCache.get(promQL, key -> executeQuery(key));
        }
        return executeQuery(promQL);
    }

    private String executeQuery(String promQL) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(prometheusUrl)
                    .path("/api/v1/query")
                    .queryParam("query", promQL)
                    .build(false) // disable encoding to preserve PromQL syntax
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
     * Execute a range query with better error handling
     */
    public String queryRange(String promQL, long start, long end, long step) {
        return queryRange(promQL, start, end, step, false);
    }

    public String queryRange(String promQL, long start, long end, long step, boolean useCache) {
        String cacheKey = String.format("%s_%d_%d_%d", promQL, start, end, step);

        if (useCache) {
            return queryCache.get(cacheKey, key -> executeRangeQuery(promQL, start, end, step));
        }
        return executeRangeQuery(promQL, start, end, step);
    }

    private String executeRangeQuery(String promQL, long start, long end, long step) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(prometheusUrl)
                    .path("/api/v1/query_range")
                    .queryParam("query", promQL)
                    .queryParam("start", start)
                    .queryParam("end", end)
                    .queryParam("step", step)
                    .build(false) // disable encoding to preserve PromQL syntax
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
     * Optimized query for getting aggregated counts over a time range
     * Uses instant query with proper time range calculation
     */
    public String queryInstant(String promQL, long start, long end) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(prometheusUrl)
                    .path("/api/v1/query")
                    .queryParam("query", promQL)
                    .queryParam("time", end) // Query at end time
                    .build(false) // disable encoding to preserve PromQL syntax
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
     * Build optimized query for RSU message counts using increase() with proper
     * time range
     * This eliminates the need for client-side time filtering
     */
    public String getRsuMessageCounts(String rsuIp, long startTime, long endTime) {
        String promQL = String.format(
                "sum by (topic) (increase(kafka_produced_rsu_messages_total{rsu_ip=\"%s\"}[%ds]))",
                rsuIp, (endTime - startTime) / 1000);
        return queryInstant(promQL, startTime, endTime);
    }

    /**
     * Build optimized query for organization RSU counts
     */
    public String getOrganizationRsuCounts(String rsuIps, String messageType, long startTime, long endTime) {
        String promQL = String.format(
                "sum by (rsu_ip, topic) (increase(kafka_produced_rsu_messages_total{rsu_ip=~\"%s\"}[%ds]))",
                rsuIps, (endTime - startTime) / 1000);
        return queryInstant(promQL, startTime, endTime);
    }

    /**
     * Build optimized query for organization RSU counts filtered by topic
     */
    public String getOrganizationRsuCountsByTopic(String rsuIps, String topic, long startTime, long endTime) {
        String promQL = String.format(
                "sum by (rsu_ip, topic) (increase(kafka_produced_rsu_messages_total{rsu_ip=~\"%s\", topic=\"%s\"}[%ds]))",
                rsuIps, topic, (endTime - startTime) / 1000);
        return queryInstant(promQL, startTime, endTime);
    }

    /**
     * Build optimized query for available topics
     */
    public String getAvailableTopicCounts(long startTime, long endTime) {
        String promQL = String.format(
                "sum by (topic) (increase(kafka_produced_rsu_messages_total{}[%ds]))",
                (endTime - startTime) / 1000);
        return queryInstant(promQL, startTime, endTime);
    }

    /**
     * Parse Prometheus response and extract metric values
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

    /**
     * Clear the query cache
     */
    public void clearCache() {
        queryCache.invalidateAll();
        log.debug("Prometheus query cache cleared");
    }

    /**
     * Get cache statistics
     */
    public String getCacheStats() {
        return String.format("Cache stats: size=%d, hitRate=%.2f",
                queryCache.estimatedSize(),
                queryCache.stats().hitRate());
    }
}