package us.dot.its.jpo.ode.api.accessorTests.counts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.accessors.counts.CountsRepositoryImpl;
import us.dot.its.jpo.ode.api.models.MessageCount;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.services.PrometheusService;

@ExtendWith(MockitoExtension.class)
public class CountsRepositoryImplTest {

    @Mock
    private PrometheusService prometheusService;

    @Mock
    private RsuRepository rsuRepository;

    private CountsRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new CountsRepositoryImpl(prometheusService, rsuRepository);
    }

    private Rsu mockRsu(String ip, String route) throws Exception {
        Rsu rsu = new Rsu();
        rsu.setIpv4Address(InetAddress.getByName(ip));
        rsu.setPrimaryRoute(route);
        return rsu;
    }

    @Test
    void testGetMessageCounts() throws Exception {
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

        when(prometheusService.getRsuMessageCounts(rsuIp, startTime.longValue(), endTime.longValue()))
                .thenReturn(mockPrometheusResponse);
        when(rsuRepository.findByIpv4Address(InetAddress.getByName(rsuIp)))
                .thenReturn(mockRsu(rsuIp, "I-25"));

        List<MessageCount> result = repository.getRsuMessageCounts(rsuIp, List.of("BSM"), startTime, endTime);

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

        when(prometheusService.getRsuMessageCounts(rsuIp, startTime.longValue(), endTime.longValue()))
                .thenReturn(mockPrometheusResponse);
        when(rsuRepository.findByIpv4Address(InetAddress.getByName(rsuIp)))
                .thenReturn(mockRsu(rsuIp, "I-70"));

        List<MessageCount> result = repository.getRsuMessageCounts(rsuIp, List.of("BSM"), startTime, endTime);

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

        when(prometheusService.getRsuMessageCounts(rsuIp, startTime.longValue(), endTime.longValue()))
                .thenReturn(mockPrometheusResponse);
        when(rsuRepository.findByIpv4Address(InetAddress.getByName(rsuIp)))
                .thenReturn(mockRsu(rsuIp, "I-25"));

        List<MessageCount> result = repository.getRsuMessageCounts(rsuIp, List.of("BSM"), startTime, endTime);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("BSM", result.get(0).getMessageType());
        assertEquals(rsuIp, result.get(0).getRsuIp());
        assertEquals(75L, result.get(0).getOdeInputCount());
        assertEquals(150L, result.get(0).getOdeOutputCount());
        assertEquals("I-25", result.get(0).getRoad());
    }

    @Test
    void testGetMessageCounts_MultipleTypes() throws Exception {
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
                            },
                            {
                                "metric": {
                                    "topic": "topic.OdeMapJson",
                                    "rsu_ip": "10.11.81.13"
                                },
                                "value": [1640995200, "20"]
                            }
                        ]
                    }
                }
                """;

        when(prometheusService.getRsuMessageCounts(rsuIp, startTime.longValue(), endTime.longValue()))
                .thenReturn(mockPrometheusResponse);
        when(rsuRepository.findByIpv4Address(InetAddress.getByName(rsuIp)))
                .thenReturn(mockRsu(rsuIp, "I-25"));

        List<MessageCount> result = repository.getRsuMessageCounts(rsuIp, List.of("BSM", "MAP", "SPAT"), startTime,
                endTime);

        assertEquals(3, result.size());
        assertEquals("BSM", result.get(0).getMessageType());
        assertEquals(150L, result.get(0).getOdeOutputCount());
        assertEquals("MAP", result.get(1).getMessageType());
        assertEquals(20L, result.get(1).getOdeOutputCount());
        assertEquals("SPAT", result.get(2).getMessageType());
        assertEquals(0L, result.get(2).getOdeInputCount());
        assertEquals(0L, result.get(2).getOdeOutputCount());
    }

    @Test
    void testGetOrganizationMessageCounts() throws Exception {
        String organization = "TestOrg";
        String messageType = "BSM";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        when(rsuRepository.findAllByOrganization(eq(organization), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        mockRsu("10.11.81.13", "I-25"),
                        mockRsu("10.11.81.14", "I-70"))));

        String mockResponse = """
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
                            },
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
                            },
                            {
                                "metric": {
                                    "topic": "topic.OdeMapJson",
                                    "rsu_ip": "10.11.81.13"
                                },
                                "value": [1640995200, "999"]
                            }
                        ]
                    }
                }
                """;

        when(prometheusService.getOrganizationRsuCounts(any(), eq(startTime.longValue()), eq(endTime.longValue())))
                .thenReturn(mockResponse);

        List<MessageCount> result = repository.getRsuOrganizationMessageCounts(
                organization, messageType, startTime, endTime);

        assertNotNull(result);
        assertEquals(2, result.size());

        MessageCount rsu1 = result.stream()
                .filter(mc -> mc.getRsuIp().equals("10.11.81.13"))
                .findFirst().orElse(null);
        assertNotNull(rsu1);
        assertEquals("BSM", rsu1.getMessageType());
        assertEquals(50L, rsu1.getOdeInputCount());
        assertEquals(100L, rsu1.getOdeOutputCount());
        assertEquals("I-25", rsu1.getRoad());

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
        String organization = "EmptyOrg";
        String messageType = "BSM";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        when(rsuRepository.findAllByOrganization(eq(organization), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        List<MessageCount> result = repository.getRsuOrganizationMessageCounts(
                organization, messageType, startTime, endTime);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetMessageCounts_PrometheusError() throws Exception {
        String rsuIp = "10.11.81.13";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        when(prometheusService.getRsuMessageCounts(rsuIp, startTime.longValue(), endTime.longValue()))
                .thenThrow(new RuntimeException("Prometheus error"));
        when(rsuRepository.findByIpv4Address(InetAddress.getByName(rsuIp)))
                .thenReturn(mockRsu(rsuIp, "I-25"));

        List<MessageCount> result = repository.getRsuMessageCounts(rsuIp, List.of("BSM"), startTime, endTime);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(0L, result.get(0).getOdeInputCount());
        assertEquals(0L, result.get(0).getOdeOutputCount());
        assertEquals("BSM", result.get(0).getMessageType());
        assertEquals(rsuIp, result.get(0).getRsuIp());
        assertEquals("I-25", result.get(0).getRoad());
    }

    @Test
    void testGetOrganizationMessageCounts_PrometheusError() throws Exception {
        String organization = "TestOrg";
        String messageType = "BSM";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;

        when(rsuRepository.findAllByOrganization(eq(organization), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        mockRsu("10.11.81.13", "I-25"),
                        mockRsu("10.11.81.14", "I-70"))));
        when(prometheusService.getOrganizationRsuCounts(any(), eq(startTime.longValue()), eq(endTime.longValue())))
                .thenThrow(new RuntimeException("Prometheus error"));

        List<MessageCount> result = repository.getRsuOrganizationMessageCounts(
                organization, messageType, startTime, endTime);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(mc -> mc.getOdeInputCount() == 0 && mc.getOdeOutputCount() == 0));
    }

    @Test
    void testGetMessageCounts_PrometheusOomPropagatesAsBadRequest() throws Exception {
        String rsuIp = "10.11.81.13";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;
        ResponseStatusException oom = new ResponseStatusException(HttpStatus.BAD_REQUEST,
                PrometheusService.OOM_USER_MESSAGE);

        when(prometheusService.getRsuMessageCounts(rsuIp, startTime.longValue(), endTime.longValue()))
                .thenThrow(oom);
        when(rsuRepository.findByIpv4Address(InetAddress.getByName(rsuIp)))
                .thenReturn(mockRsu(rsuIp, "I-25"));

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> repository.getRsuMessageCounts(rsuIp, List.of("BSM"), startTime, endTime));

        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
        assertEquals(PrometheusService.OOM_USER_MESSAGE, thrown.getReason());
    }

    @Test
    void testGetOrganizationMessageCounts_PrometheusOomPropagatesAsBadRequest() throws Exception {
        String organization = "TestOrg";
        String messageType = "BSM";
        Long startTime = 1640995200000L;
        Long endTime = 1641081600000L;
        ResponseStatusException oom = new ResponseStatusException(HttpStatus.BAD_REQUEST,
                PrometheusService.OOM_USER_MESSAGE);

        when(rsuRepository.findAllByOrganization(eq(organization), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mockRsu("10.11.81.13", "I-25"))));
        when(prometheusService.getOrganizationRsuCounts(any(), eq(startTime.longValue()), eq(endTime.longValue())))
                .thenThrow(oom);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> repository.getRsuOrganizationMessageCounts(organization, messageType, startTime, endTime));

        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
        assertEquals(PrometheusService.OOM_USER_MESSAGE, thrown.getReason());
    }
}
