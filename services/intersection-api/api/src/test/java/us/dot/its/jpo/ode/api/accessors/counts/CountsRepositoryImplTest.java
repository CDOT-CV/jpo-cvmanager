package us.dot.its.jpo.ode.api.accessors.counts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import us.dot.its.jpo.ode.api.models.MessageCount;
import us.dot.its.jpo.ode.api.services.PrometheusService;

@ExtendWith(MockitoExtension.class)
public class CountsRepositoryImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private PrometheusService prometheusService;

    private CountsRepositoryImpl countsRepository;

    @BeforeEach
    void setUp() {
        countsRepository = new CountsRepositoryImpl(mongoTemplate, prometheusService);
    }

    @Test
    public void testGetMessageCountsFromPrometheus() {
        // Given
        String rsuIp = "192.168.1.100";
        Long startTime = 1640995200000L; // 2022-01-01 00:00:00 UTC
        Long endTime = 1641081600000L; // 2022-01-02 00:00:00 UTC

        String mockPrometheusResponse = """
                {
                    "status": "success",
                    "data": {
                        "result": [
                            {
                                "metric": {
                                    "topic": "topic.ode.bsm.rawencoded"
                                },
                                "value": [1640995200, "150"]
                            }
                        ]
                    }
                }
                """;

        when(prometheusService.query(anyString())).thenReturn(mockPrometheusResponse);

        // When
        List<MessageCount> result = countsRepository.getMessageCounts(rsuIp, startTime, endTime);

        // Then
        assertThat(result).isNotEmpty();
        // The service will query for all message types, so we expect multiple calls
        // The actual result will depend on the Prometheus response processing logic
    }

    @Test
    public void testGetMessageCountsFromPrometheusEmptyResult() {
        // Given
        String rsuIp = "192.168.1.101";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        String mockPrometheusResponse = """
                {
                    "status": "success",
                    "data": {
                        "result": []
                    }
                }
                """;

        when(prometheusService.query(anyString())).thenReturn(mockPrometheusResponse);

        // When
        List<MessageCount> result = countsRepository.getMessageCounts(rsuIp, startTime, endTime);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    public void testGetMessageCountsFromPrometheusWithMultipleMessageTypes() {
        // Given
        String rsuIp = "192.168.1.100";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        String mockPrometheusResponse = """
                {
                    "status": "success",
                    "data": {
                        "result": [
                            {
                                "metric": {
                                    "topic": "topic.ode.bsm.rawencoded"
                                },
                                "value": [1640995200, "150"]
                            },
                            {
                                "metric": {
                                    "topic": "topic.ode.bsm"
                                },
                                "value": [1640995200, "145"]
                            },
                            {
                                "metric": {
                                    "topic": "topic.ode.tim.rawencoded"
                                },
                                "value": [1640995200, "50"]
                            }
                        ]
                    }
                }
                """;

        when(prometheusService.query(anyString())).thenReturn(mockPrometheusResponse);

        // When
        List<MessageCount> result = countsRepository.getMessageCounts(rsuIp, startTime, endTime);

        // Then
        assertThat(result).isNotEmpty();
        // Should find BSM in/out counts and TIM in count
    }
}