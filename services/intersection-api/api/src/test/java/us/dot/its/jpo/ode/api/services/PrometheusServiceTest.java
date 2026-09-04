package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.net.URI;

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

        Field stepField = PrometheusService.class.getDeclaredField("aggregationStepSeconds");
        stepField.setAccessible(true);
        stepField.set(prometheusService, 60);
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
    public void testBuildSumOverTimeIncreaseQuery_UsesConfiguredStep() {
        long startTime = 1640995200000L;
        long endTime = 1641081600000L; // 24h later

        String promQL = prometheusService.buildSumOverTimeIncreaseQuery(
                "rsu_ip=\"10.0.0.1\", topic=\"topic.OdeBsmJson\"",
                "topic",
                startTime,
                endTime);

        assertThat(promQL).isEqualTo(
                "sum by (topic) (sum_over_time(increase(kafka_produced_rsu_messages_total"
                        + "{rsu_ip=\"10.0.0.1\", topic=\"topic.OdeBsmJson\"}[60s])[86400s:60s]))");
    }

    @Test
    public void testGetRsuMessageCounts_UsesSumOverTimeInstantQuery() {
        long startTime = 1640995200000L;
        long endTime = 1641081600000L;
        String expectedResponse = "{\"status\":\"success\",\"data\":{\"result\":[]}}";

        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(expectedResponse);

        String result = prometheusService.getRsuMessageCounts("10.0.0.1", "topic.OdeBsmJson", startTime, endTime);

        assertThat(result).isEqualTo(expectedResponse);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uriCaptor.capture(), eq(String.class));

        String uri = uriCaptor.getValue().toString();
        assertThat(uri).contains("/api/v1/query");
        assertThat(uri).contains("sum_over_time");
        assertThat(uri).contains("increase");
        assertThat(uri).contains("time=1641081600");
    }

    @Test
    public void testGetAvailableTopicCounts_OmitsEmptySelector() {
        long startTime = 1640995200000L;
        long endTime = 1641081600000L;

        String promQL = prometheusService.buildSumOverTimeIncreaseQuery("", "topic", startTime, endTime);

        assertThat(promQL).isEqualTo(
                "sum by (topic) (sum_over_time(increase(kafka_produced_rsu_messages_total[60s])[86400s:60s]))");
    }
}
