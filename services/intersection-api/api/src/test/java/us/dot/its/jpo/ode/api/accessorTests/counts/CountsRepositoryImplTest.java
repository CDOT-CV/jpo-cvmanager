package us.dot.its.jpo.ode.api.accessorTests.counts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import us.dot.its.jpo.ode.api.accessors.counts.CountsRepositoryImpl;
import us.dot.its.jpo.ode.api.models.MessageCount;
import us.dot.its.jpo.ode.api.services.PostgresService;
import us.dot.its.jpo.ode.api.services.PrometheusService;

@ExtendWith(MockitoExtension.class)
public class CountsRepositoryImplTest {

    @Mock
    private PrometheusService prometheusService;

    @Mock
    private PostgresService postgresService;

    private CountsRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new CountsRepositoryImpl(prometheusService, postgresService);
    }

    @Test
    void testGetMessageCounts() throws Exception {
        // Given
        String rsuIp = "10.11.81.13";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        String mockPrometheusResponse = """
                {
                    "status": "success",
                    "data": {
                        "result": [
                            {
                                "metric": {
                                    "topic": "topic.OdeBsmJson",
                                    "rsu_ip": "10.11.81.13"
                                },
                                "value": [1640995200, "150"]
                            }
                        ]
                    }
                }
                """;

        when(prometheusService.getRsuMessageCounts(rsuIp, startTime, endTime))
                .thenReturn(mockPrometheusResponse);
        when(postgresService.getRsuPrimaryRoute(rsuIp))
                .thenReturn("I-25");

        // When
        List<MessageCount> result = repository.getRsuMessageCounts(rsuIp, startTime, endTime);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("BSM", result.get(0).getMessageType());
        assertEquals(rsuIp, result.get(0).getRsuIp());
        assertEquals(0L, result.get(0).getOdeInputCount());
        assertEquals(150L, result.get(0).getOdeOutputCount());
        assertEquals("I-25", result.get(0).getRoad());
    }

    @Test
    void testGetMessageCounts_WithRawEncodedTopic() throws Exception {
        // Given
        String rsuIp = "10.11.81.13";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        String mockPrometheusResponse = """
                {
                    "status": "success",
                    "data": {
                        "result": [
                            {
                                "metric": {
                                    "topic": "topic.OdeBsmRawEncodedJson",
                                    "rsu_ip": "10.11.81.13"
                                },
                                "value": [1640995200, "75"]
                            }
                        ]
                    }
                }
                """;

        when(prometheusService.getRsuMessageCounts(rsuIp, startTime, endTime))
                .thenReturn(mockPrometheusResponse);
        when(postgresService.getRsuPrimaryRoute(rsuIp))
                .thenReturn("I-70");

        // When
        List<MessageCount> result = repository.getRsuMessageCounts(rsuIp, startTime, endTime);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("BSM", result.get(0).getMessageType());
        assertEquals(rsuIp, result.get(0).getRsuIp());
        assertEquals(75L, result.get(0).getOdeInputCount());
        assertEquals(0L, result.get(0).getOdeOutputCount());
        assertEquals("I-70", result.get(0).getRoad());
    }

    @Test
    void testGetMessageCounts_WithBothInputAndOutput() throws Exception {
        // Given
        String rsuIp = "10.11.81.13";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        String mockPrometheusResponse = """
                {
                    "status": "success",
                    "data": {
                        "result": [
                            {
                                "metric": {
                                    "topic": "topic.OdeBsmRawEncodedJson",
                                    "rsu_ip": "10.11.81.13"
                                },
                                "value": [1640995200, "75"]
                            },
                            {
                                "metric": {
                                    "topic": "topic.OdeBsmJson",
                                    "rsu_ip": "10.11.81.13"
                                },
                                "value": [1640995200, "150"]
                            }
                        ]
                    }
                }
                """;

        when(prometheusService.getRsuMessageCounts(rsuIp, startTime, endTime))
                .thenReturn(mockPrometheusResponse);
        when(postgresService.getRsuPrimaryRoute(rsuIp))
                .thenReturn("I-25");

        // When
        List<MessageCount> result = repository.getRsuMessageCounts(rsuIp, startTime, endTime);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size()); // Should be consolidated into one object
        assertEquals("BSM", result.get(0).getMessageType());
        assertEquals(rsuIp, result.get(0).getRsuIp());
        assertEquals(75L, result.get(0).getOdeInputCount());
        assertEquals(150L, result.get(0).getOdeOutputCount());
        assertEquals("I-25", result.get(0).getRoad());
    }

    @Test
    void testGetOrganizationMessageCounts() throws Exception {
        // Given
        String organization = "TestOrg";
        String messageType = "BSM";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        Map<String, String> rsuIpToRoadMap = new HashMap<>();
        rsuIpToRoadMap.put("10.11.81.13", "I-25");
        rsuIpToRoadMap.put("10.11.81.14", "I-70");

        String mockTopicsResponse = """
                {
                    "status": "success",
                    "data": {
                        "result": [
                            {
                                "metric": {
                                    "topic": "topic.OdeBsmJson"
                                }
                            },
                            {
                                "metric": {
                                    "topic": "topic.OdeBsmRawEncodedJson"
                                }
                            }
                        ]
                    }
                }
                """;

        String mockInResponse = """
                {
                    "status": "success",
                    "data": {
                        "result": [
                            {
                                "metric": {
                                    "topic": "topic.OdeBsmRawEncodedJson",
                                    "rsu_ip": "10.11.81.13"
                                },
                                "value": [1640995200, "50"]
                            },
                            {
                                "metric": {
                                    "topic": "topic.OdeBsmRawEncodedJson",
                                    "rsu_ip": "10.11.81.14"
                                },
                                "value": [1640995200, "25"]
                            }
                        ]
                    }
                }
                """;

        String mockOutResponse = """
                {
                    "status": "success",
                    "data": {
                        "result": [
                            {
                                "metric": {
                                    "topic": "topic.OdeBsmJson",
                                    "rsu_ip": "10.11.81.13"
                                },
                                "value": [1640995200, "100"]
                            },
                            {
                                "metric": {
                                    "topic": "topic.OdeBsmJson",
                                    "rsu_ip": "10.11.81.14"
                                },
                                "value": [1640995200, "50"]
                            }
                        ]
                    }
                }
                """;

        when(postgresService.getOrganizationRsuIps(organization))
                .thenReturn(rsuIpToRoadMap);
        when(prometheusService.getAvailableTopicCounts(startTime, endTime))
                .thenReturn(mockTopicsResponse);
        when(prometheusService.getOrganizationRsuCountsByTopic(anyString(), eq("topic.OdeBsmRawEncodedJson"),
                eq(startTime), eq(endTime)))
                .thenReturn(mockInResponse);
        when(prometheusService.getOrganizationRsuCountsByTopic(anyString(), eq("topic.OdeBsmJson"), eq(startTime),
                eq(endTime)))
                .thenReturn(mockOutResponse);

        // When
        List<MessageCount> result = repository.getRsuOrganizationMessageCounts(
                organization, messageType, startTime, endTime);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size()); // 2 RSUs with consolidated counts

        // Verify first RSU counts
        MessageCount rsu1 = result.stream()
                .filter(mc -> mc.getRsuIp().equals("10.11.81.13"))
                .findFirst().orElse(null);
        assertNotNull(rsu1);
        assertEquals("BSM", rsu1.getMessageType());
        assertEquals(50L, rsu1.getOdeInputCount());
        assertEquals(100L, rsu1.getOdeOutputCount());
        assertEquals("I-25", rsu1.getRoad());

        // Verify second RSU counts
        MessageCount rsu2 = result.stream()
                .filter(mc -> mc.getRsuIp().equals("10.11.81.14"))
                .findFirst().orElse(null);
        assertNotNull(rsu2);
        assertEquals("BSM", rsu2.getMessageType());
        assertEquals(25L, rsu2.getOdeInputCount());
        assertEquals(50L, rsu2.getOdeOutputCount());
        assertEquals("I-70", rsu2.getRoad());
    }

    @Test
    void testGetOrganizationMessageCounts_EmptyOrganization() {
        // Given
        String organization = "EmptyOrg";
        String messageType = "BSM";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        when(postgresService.getOrganizationRsuIps(organization))
                .thenReturn(new HashMap<>());

        // When
        List<MessageCount> result = repository.getRsuOrganizationMessageCounts(
                organization, messageType, startTime, endTime);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetMessageCounts_PrometheusError() {
        // Given
        String rsuIp = "10.11.81.13";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        when(prometheusService.getRsuMessageCounts(rsuIp, startTime, endTime))
                .thenThrow(new RuntimeException("Prometheus error"));

        // When
        List<MessageCount> result = repository.getRsuMessageCounts(rsuIp, startTime, endTime);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetOrganizationMessageCounts_PrometheusError() {
        // Given
        String organization = "TestOrg";
        String messageType = "BSM";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        Map<String, String> rsuIpToRoadMap = new HashMap<>();
        rsuIpToRoadMap.put("10.11.81.13", "I-25");
        rsuIpToRoadMap.put("10.11.81.14", "I-70");

        when(postgresService.getOrganizationRsuIps(organization))
                .thenReturn(rsuIpToRoadMap);
        when(prometheusService.getAvailableTopicCounts(startTime, endTime))
                .thenThrow(new RuntimeException("Prometheus error"));

        // When
        List<MessageCount> result = repository.getRsuOrganizationMessageCounts(
                organization, messageType, startTime, endTime);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}