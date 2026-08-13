package us.dot.its.jpo.ode.api.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.models.postgres.projections.RsuOnlineStatusProjection;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

@ExtendWith(MockitoExtension.class)
class RsuOnlineStatusServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");

    @Mock
    private RsuRepository rsuRepository;

    private RsuOnlineStatusService service;

    @BeforeEach
    void setUp() {
        service = new RsuOnlineStatusService(rsuRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void getOnlineStatuses_calculatesOnlineUnstableAndOffline() throws Exception {
        when(rsuRepository.findOnlineStatusPingsByOrganization(eq("TestOrg"), any())).thenReturn(List.of(
                ping("10.0.0.1", NOW.minusSeconds(60), true),
                ping("10.0.0.2", NOW.minusSeconds(30), false),
                ping("10.0.0.2", NOW.minusSeconds(90), true),
                ping("10.0.0.3", NOW.minusSeconds(45), false),
                ping("10.0.0.4", null, null)));

        var statuses = service.getOnlineStatuses("TestOrg");

        assertEquals("online", statuses.get("10.0.0.1").getCurrentStatus());
        assertEquals("unstable", statuses.get("10.0.0.2").getCurrentStatus());
        assertEquals("offline", statuses.get("10.0.0.3").getCurrentStatus());
        assertEquals("offline", statuses.get("10.0.0.4").getCurrentStatus());
    }

    @Test
    void getLastOnline_returnsLatestSuccessfulPingTimestamp() throws Exception {
        Instant lastOnline = NOW.minusSeconds(30);
        when(rsuRepository.existsByIpAndOrganization(any(), eq("TestOrg"))).thenReturn(true);
        when(rsuRepository.findLatestSuccessfulPingTimestamp(any(), eq("TestOrg")))
                .thenReturn(Optional.of(lastOnline));

        var result = service.getLastOnline("TestOrg", "10.0.0.1");

        assertEquals("10.0.0.1", result.getIp());
        assertEquals(lastOnline, result.getLastOnline());
    }

    @Test
    void getLastOnline_returnsNullWhenAuthorizedRsuHasNoSuccessfulPing() throws Exception {
        when(rsuRepository.existsByIpAndOrganization(any(), eq("TestOrg"))).thenReturn(true);
        when(rsuRepository.findLatestSuccessfulPingTimestamp(any(), eq("TestOrg")))
                .thenReturn(Optional.empty());

        var result = service.getLastOnline("TestOrg", "10.0.0.1");

        assertEquals("10.0.0.1", result.getIp());
        assertNull(result.getLastOnline());
    }

    @Test
    void getLastOnline_returnsNotFoundForRsuOutsideOrganization() {
        when(rsuRepository.existsByIpAndOrganization(any(), eq("TestOrg"))).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.getLastOnline("TestOrg", "10.0.0.1"));

        assertEquals(404, exception.getStatusCode().value());
    }

    private static RsuOnlineStatusProjection ping(String ip, Instant timestamp, Boolean result) throws Exception {
        InetAddress address = InetAddress.getByName(ip);
        return new RsuOnlineStatusProjection() {
            @Override
            public InetAddress getIpv4Address() {
                return address;
            }

            @Override
            public Instant getTimestamp() {
                return timestamp;
            }

            @Override
            public Boolean getResult() {
                return result;
            }
        };
    }
}
