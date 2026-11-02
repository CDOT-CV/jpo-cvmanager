package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import us.dot.its.jpo.ode.api.models.postgres.projections.RsuOnlineStatusProjection;
import us.dot.its.jpo.ode.api.models.rsu.LastOnlineDto;
import us.dot.its.jpo.ode.api.models.rsu.OnlineStatusDto;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

@Service
@RequiredArgsConstructor
public class RsuOnlineStatusService {
    private static final Duration STATUS_WINDOW = Duration.ofMinutes(20);

    private final RsuRepository rsuRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Map<String, OnlineStatusDto> getOnlineStatuses(String organization) {
        Instant cutoff = clock.instant().minus(STATUS_WINDOW);
        List<RsuOnlineStatusProjection> pings = rsuRepository.findOnlineStatusPingsByOrganization(organization, cutoff);
        return pings.stream()
                .collect(Collectors.groupingBy(
                        ping -> ping.getIpv4Address().getHostAddress(),
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), RsuOnlineStatusService::statusForGroup)));
    }

    @Transactional(readOnly = true)
    public LastOnlineDto getLastOnline(String ip) {
        InetAddress address = RsuManagementService.toIpv4Address(ip);
        Instant lastOnline = rsuRepository.findLatestSuccessfulPingTimestamp(address)
                .orElse(null);
        return new LastOnlineDto(address.getHostAddress(), lastOnline);
    }

    private static OnlineStatusDto statusForGroup(List<RsuOnlineStatusProjection> group) {
        List<RsuOnlineStatusProjection> timestampedPings = group.stream()
                .filter(ping -> ping.getTimestamp() != null)
                .sorted(Comparator.comparing(RsuOnlineStatusProjection::getTimestamp).reversed())
                .toList();

        boolean hasPing = !timestampedPings.isEmpty();
        boolean firstPingSuccessful = hasPing && Boolean.TRUE.equals(timestampedPings.getFirst().getResult());
        boolean hasSuccessfulPing = timestampedPings.stream()
                .anyMatch(ping -> Boolean.TRUE.equals(ping.getResult()));

        return new OnlineStatusDto(statusFor(hasPing, firstPingSuccessful, hasSuccessfulPing));
    }

    private static String statusFor(boolean hasPing, boolean firstPingSuccessful, boolean hasSuccessfulPing) {
        if (!hasPing || !hasSuccessfulPing) {
            return "offline";
        }
        return firstPingSuccessful ? "online" : "unstable";
    }
}
