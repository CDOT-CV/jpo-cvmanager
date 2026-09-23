package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@ExtendWith(MockitoExtension.class)
public class PrometheusServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private PrometheusService prometheusService;

    @BeforeEach
    void setUp() throws Exception {
        prometheusService = new PrometheusService(restTemplate);

        Field prometheusUrlField = PrometheusService.class.getDeclaredField("prometheusUrl");
        prometheusUrlField.setAccessible(true);
        prometheusUrlField.set(prometheusService, "http://localhost:9090");

        Field lookbehindField = PrometheusService.class.getDeclaredField("minLookbehindSeconds");
        lookbehindField.setAccessible(true);
        lookbehindField.set(prometheusService, 60);
    }

    @Test
    public void testQuery() {
        String promQL = "up";
        String expectedResponse = "{\"status\":\"success\",\"data\":{\"result\":[]}}";

        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(expectedResponse);

        String result = prometheusService.query(promQL);

        assertThat(result).isEqualTo(expectedResponse);
    }

    @Test
    public void testQueryRange() {
        String promQL = "up";
        long start = 1640995200L;
        long end = 1641081600L;
        long step = 300L;
        String expectedResponse = "{\"status\":\"success\",\"data\":{\"result\":[]}}";

        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(expectedResponse);

        String result = prometheusService.queryRange(promQL, start, end, step);

        assertThat(result).isEqualTo(expectedResponse);
    }

    @Test
    public void testBuildIncreaseQuery_UsesWindowAndMinLookbehindFloor() {
        long startTime = 1640995200000L;
        long endTime = 1641081600000L; // 24h later

        String promQL = prometheusService.buildIncreaseQuery(
                "rsu_ip=\"10.0.0.1\"",
                "topic",
                startTime,
                endTime);

        assertThat(promQL).isEqualTo(
                "sum by (topic) (increase(kafka_produced_rsu_messages_total{rsu_ip=\"10.0.0.1\"}[86400s]))");
        assertThat(promQL).doesNotContain("sum_over_time");
        assertThat(promQL).doesNotContain(":60s]");
    }

    @Test
    public void testBuildIncreaseQuery_OmitsEmptySelector() {
        long startTime = 1640995200000L;
        long endTime = 1641081600000L;

        String promQL = prometheusService.buildIncreaseQuery("", "topic", startTime, endTime);

        assertThat(promQL).isEqualTo(
                "sum by (topic) (increase(kafka_produced_rsu_messages_total[86400s]))");
    }

    @Test
    public void testGetRsuMessageCounts_UsesIncreaseInstantQuery() {
        long startTime = 1640995200000L;
        long endTime = 1641081600000L;
        String expectedResponse = "{\"status\":\"success\",\"data\":{\"result\":[]}}";

        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(expectedResponse);

        String result = prometheusService.getRsuMessageCounts("10.0.0.1", startTime, endTime);

        assertThat(result).isEqualTo(expectedResponse);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uriCaptor.capture(), eq(String.class));

        String uri = uriCaptor.getValue().toString();
        assertThat(uri).contains("/api/v1/query");
        assertThat(uri).contains("increase");
        assertThat(uri).doesNotContain("sum_over_time");
        assertThat(uri).contains("time=1641081600");
        assertThat(uri).contains("rsu_ip");
    }

    @Test
    public void testGetOrganizationRsuCounts_IncludesTopicSelector() {
        long startTime = 1640995200000L;
        long endTime = 1641081600000L;
        String expectedResponse = "{\"status\":\"success\",\"data\":{\"result\":[]}}";

        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(expectedResponse);

        prometheusService.getOrganizationRsuCounts("10.0.0.1|10.0.0.2", "topic\\.Ode.*[Bb][Ss][Mm].*Json",
                startTime, endTime);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uriCaptor.capture(), eq(String.class));

        String query = uriCaptor.getValue().getQuery();
        assertThat(query).contains("rsu_ip");
        assertThat(query).contains("topic");
        assertThat(query).doesNotContain("sum_over_time");
    }

    @Test
    public void testBaselineQuery_KeepsFrozenSubqueryShape() {
        String baseline = PrometheusService.buildBaselineSumOverTimeIncreaseQuery(
                "rsu_ip=\"10.0.0.1\", topic=\"topic.OdeBsmJson\"",
                "topic",
                86400,
                120);

        assertThat(baseline).isEqualTo(
                "sum by (topic) (sum_over_time(increase(kafka_produced_rsu_messages_total"
                        + "{rsu_ip=\"10.0.0.1\", topic=\"topic.OdeBsmJson\"}[120s])[86400s:120s]))");
    }

    @Test
    public void testGetRsuMessageCounts_MapsVictoriaMetricsOomToBadRequest() {
        String oomBody = "{\"status\":\"error\",\"errorType\":\"422\",\"error\":"
                + "\"cannot allocate more memory: requested 33554432 bytes, remaining 1048576 bytes\"}";
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenThrow(unprocessableEntity(oomBody));

        assertThatThrownBy(() -> prometheusService.getRsuMessageCounts("10.0.0.1", 1640995200000L, 1641081600000L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo(PrometheusService.OOM_USER_MESSAGE);
                });
    }

    @Test
    public void testGetOrganizationRsuCounts_MapsNonOom422ToBadRequest() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenThrow(unprocessableEntity("{\"status\":\"error\",\"error\":\"invalid query\"}"));

        assertThatThrownBy(
                () -> prometheusService.getOrganizationRsuCounts("10.0.0.1|10.0.0.2",
                        "topic\\.Ode.*[Bb][Ss][Mm].*Json", 1640995200000L, 1641081600000L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo(PrometheusService.UNPROCESSABLE_USER_MESSAGE);
                });
    }

    @Test
    public void testQueryInstant_GenericFailureStaysRuntimeException() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> prometheusService.queryInstant("up", 1641081600000L))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Failed to query Prometheus");
    }

    @Test
    public void testIsOutOfMemoryError() {
        assertThat(PrometheusService.isOutOfMemoryError(
                "cannot allocate more memory: requested 1 bytes, remaining 0 bytes")).isTrue();
        assertThat(PrometheusService.isOutOfMemoryError("query exceeds maxMemoryPerQuery")).isTrue();
        assertThat(PrometheusService.isOutOfMemoryError("out of memory")).isTrue();
        assertThat(PrometheusService.isOutOfMemoryError("invalid query")).isFalse();
        assertThat(PrometheusService.isOutOfMemoryError(null)).isFalse();
    }

    private static HttpClientErrorException unprocessableEntity(String body) {
        return HttpClientErrorException.create(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Unprocessable Entity",
                HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
    }
}
