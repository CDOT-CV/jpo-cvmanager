package us.dot.its.jpo.ode.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        prometheusService = new PrometheusService();

        // Use reflection to set the restTemplate field
        Field restTemplateField = PrometheusService.class.getDeclaredField("restTemplate");
        restTemplateField.setAccessible(true);
        restTemplateField.set(prometheusService, restTemplate);

        // Use reflection to set the prometheusUrl field
        Field prometheusUrlField = PrometheusService.class.getDeclaredField("prometheusUrl");
        prometheusUrlField.setAccessible(true);
        prometheusUrlField.set(prometheusService, "http://localhost:9090");

        // Use reflection to set the timeoutSeconds field
        Field timeoutField = PrometheusService.class.getDeclaredField("timeoutSeconds");
        timeoutField.setAccessible(true);
        timeoutField.set(prometheusService, 30);
    }

    @Test
    public void testQuery() {
        // Given
        String promQL = "up";
        String expectedResponse = "{\"status\":\"success\",\"data\":{\"result\":[]}}";

        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(expectedResponse);

        // When
        String result = prometheusService.query(promQL);

        // Then
        assertThat(result).isEqualTo(expectedResponse);
    }

    @Test
    public void testQueryRange() {
        // Given
        String promQL = "up";
        long start = 1640995200L; // 2022-01-01 00:00:00 UTC
        long end = 1641081600L; // 2022-01-02 00:00:00 UTC
        long step = 300L; // 5 minutes
        String expectedResponse = "{\"status\":\"success\",\"data\":{\"result\":[]}}";

        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(expectedResponse);

        // When
        String result = prometheusService.queryRange(promQL, start, end, step);

        // Then
        assertThat(result).isEqualTo(expectedResponse);
    }
}