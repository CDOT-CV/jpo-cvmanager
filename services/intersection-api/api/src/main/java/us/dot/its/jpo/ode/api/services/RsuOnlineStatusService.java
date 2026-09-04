package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import us.dot.its.jpo.ode.api.models.postgres.projections.RsuOnlineStatusProjection;
import us.dot.its.jpo.ode.api.models.rsu.LastOnlineDto;
import us.dot.its.jpo.ode.api.models.rsu.OnlineStatusDto;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

@Service
@RequiredArgsConstructor
public class RsuOnlineStatusService {
    private static final Duration STATUS_WINDOW = Duration.ofMinutes(20);
    private static final String IPV4_PATTERN = "^\\d{1,3}(?:\\.\\d{1,3}){3}$";

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
        InetAddress address = toIpv4Address(ip);
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

    private static InetAddress toIpv4Address(String ip) {
        if (!ip.matches(IPV4_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IP address must be IPv4");
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.getAddress().length != 4) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IP address must be IPv4");
            }
            return address;
        } catch (UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid IP address", exception);
        }
    }
}
