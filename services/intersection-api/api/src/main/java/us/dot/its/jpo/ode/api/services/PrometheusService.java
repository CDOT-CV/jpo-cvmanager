package us.dot.its.jpo.ode.api.services;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
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
    public static final String OOM_USER_MESSAGE =
            "The message counts query ran out of memory. Please select a shorter time range.";
    public static final String UNPROCESSABLE_USER_MESSAGE =
            "The counts query could not be processed. Please select a shorter time range and try again.";

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Cache<String, String> queryCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    @Value("${prometheus.url:http://localhost:9090}")
    private String prometheusUrl;

    /**
     * Minimum increase() lookbehind in seconds. Used as a floor so short UI ranges
     * still contain at least two samples.
     */
    @Value("${prometheus.aggregation.step.seconds:120}")
    private int minLookbehindSeconds;

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
            throw wrapPrometheusFailure("querying Prometheus", e);
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
            throw wrapPrometheusFailure("querying Prometheus range", e);
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
            throw wrapPrometheusFailure("querying Prometheus instant", e);
        }
    }

    /**
     * Maps VictoriaMetrics/Prometheus HTTP 422 (including query OOM) to HTTP 400 so
     * the counts API can tell the client to shorten the time range.
     */
    RuntimeException wrapPrometheusFailure(String action, Exception e) {
        if (e instanceof ResponseStatusException responseStatusException) {
            return responseStatusException;
        }
        if (e instanceof HttpStatusCodeException httpEx && httpEx.getStatusCode().value() == 422) {
            String body = httpEx.getResponseBodyAsString();
            String message = isOutOfMemoryError(body) ? OOM_USER_MESSAGE : UNPROCESSABLE_USER_MESSAGE;
            log.warn("Prometheus returned 422 during {}: {}", action, body);
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, message, httpEx);
        }
        log.error("Error {}: {}", action, e.getMessage());
        return new RuntimeException("Failed to query Prometheus", e);
    }

    static boolean isOutOfMemoryError(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String lower = body.toLowerCase();
        return lower.contains("cannot allocate more memory")
                || lower.contains("out of memory")
                || lower.contains("maxmemoryperquery");
    }

    /**
     * Builds {@code sum by (labels) (increase(metric[range]))} so increments from
     * scaled/restarted ODE hosts (distinct {@code instance}/{@code host} series) are
     * included without a memory-heavy subquery.
     * <p>
     * On Prometheus, {@code increase()} extrapolates about one scrape interval at
     * series edges. VictoriaMetrics does not extrapolate.
     *
     * @param metricSelector label selector body, e.g. {@code rsu_ip="1.2.3.4"}
     * @param groupBy        comma-separated label names for {@code sum by (...)}
     * @param startTime      start time in milliseconds
     * @param endTime        end time in milliseconds
     * @return PromQL string
     */
    String buildIncreaseQuery(String metricSelector, String groupBy, long startTime, long endTime) {
        long rangeSeconds = Math.max((endTime - startTime) / 1000, minLookbehindSeconds);
        String metric = metricSelector == null || metricSelector.isBlank()
                ? METRIC_NAME
                : String.format("%s{%s}", METRIC_NAME, metricSelector);
        return String.format("sum by (%s) (increase(%s[%ds]))", groupBy, metric, rangeSeconds);
    }

    /**
     * Frozen previous production query. Kept for accuracy comparisons against the
     * {@code increase([range])} query. Do not use for live counts.
     */
    static String buildBaselineSumOverTimeIncreaseQuery(String metricSelector, String groupBy, long rangeSeconds,
            int stepSeconds) {
        String metric = metricSelector == null || metricSelector.isBlank()
                ? METRIC_NAME
                : String.format("%s{%s}", METRIC_NAME, metricSelector);
        return String.format(
                "sum by (%s) (sum_over_time(increase(%s[%ds])[%ds:%ds]))",
                groupBy, metric, stepSeconds, rangeSeconds, stepSeconds);
    }

    /**
     * RSU message counts for a single RSU IP over a time range, grouped by topic.
     * Uses {@code sum by (topic) (increase(...[range]))} so increments from
     * scaled/restarted ODE hosts are included.
     *
     * @param rsuIp     the IP address of the RSU
     * @param startTime start time in milliseconds
     * @param endTime   end time in milliseconds
     * @return the JSON response from Prometheus
     */
    public String getRsuMessageCounts(String rsuIp, long startTime, long endTime) {
        String selector = String.format("rsu_ip=\"%s\"", rsuIp);
        String promQL = buildIncreaseQuery(selector, "topic", startTime, endTime);
        return queryInstant(promQL, endTime);
    }

    /**
     * Organization RSU counts over a time range, grouped by RSU IP and topic.
     *
     * @param rsuIps     pipe-delimited RSU IP regex pattern
     * @param topicRegex PromQL regex for {@code topic} (input + output names for one
     *                   message type). Null/blank matches all topics.
     * @param startTime  start time in milliseconds
     * @param endTime    end time in milliseconds
     * @return the JSON response from Prometheus
     */
    public String getOrganizationRsuCounts(String rsuIps, String topicRegex, long startTime, long endTime) {
        String selector = topicRegex == null || topicRegex.isBlank()
                ? String.format("rsu_ip=~\"%s\"", rsuIps)
                : String.format("rsu_ip=~\"%s\", topic=~\"%s\"", rsuIps, topicRegex);
        String promQL = buildIncreaseQuery(selector, "rsu_ip, topic", startTime, endTime);
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

            if (prometheusResponse.isSuccess()) {
                return prometheusResponse.getResults().stream()
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
