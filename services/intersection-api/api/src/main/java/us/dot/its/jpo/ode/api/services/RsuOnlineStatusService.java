package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import us.dot.its.jpo.ode.api.models.rsu.LastOnlineDto;
import us.dot.its.jpo.ode.api.models.rsu.OnlineStatusDto;
import us.dot.its.jpo.ode.api.repositories.RsuOnlineStatusProjection;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

@Service
@RequiredArgsConstructor
public class RsuOnlineStatusService {
    private static final Duration STATUS_WINDOW = Duration.ofMinutes(20);
    private static final String IPV4_PATTERN = "^\\d{1,3}(?:\\.\\d{1,3}){3}$";

    private final RsuRepository rsuRepository;
    private final Clock clock;

    public Map<String, OnlineStatusDto> getOnlineStatuses(String organization) {
        Instant cutoff = clock.instant().minus(STATUS_WINDOW);
        List<RsuOnlineStatusProjection> pings = rsuRepository.findOnlineStatusPingsByOrganization(organization, cutoff);
        Map<String, OnlineStatusDto> statuses = new LinkedHashMap<>();

        String currentIp = null;
        boolean hasSuccessfulPing = false;
        boolean firstPingSuccessful = false;
        boolean hasPing = false;
        for (RsuOnlineStatusProjection ping : pings) {
            String ip = ping.getIpv4Address().getHostAddress();
            if (!ip.equals(currentIp)) {
                if (currentIp != null) {
                    statuses.put(currentIp, new OnlineStatusDto(statusFor(hasPing, firstPingSuccessful, hasSuccessfulPing)));
                }
                currentIp = ip;
                hasPing = false;
                firstPingSuccessful = false;
                hasSuccessfulPing = false;
            }
            if (ping.getTimestamp() != null) {
                boolean isFirstPing = !hasPing;
                hasPing = true;
                boolean success = Boolean.TRUE.equals(ping.getResult());
                if (isFirstPing) {
                    // The query is newest-first, so this assignment only applies to the first ping.
                    firstPingSuccessful = success;
                }
                hasSuccessfulPing |= success;
            }
        }
        if (currentIp != null) {
            statuses.put(currentIp, new OnlineStatusDto(statusFor(hasPing, firstPingSuccessful, hasSuccessfulPing)));
        }
        return statuses;
    }

    public LastOnlineDto getLastOnline(String organization, String ip) {
        InetAddress address = toIpv4Address(ip);
        if (!rsuRepository.existsByIpAndOrganization(address, organization)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RSU not found");
        }
        Instant lastOnline = rsuRepository.findPingsByIpAndOrganization(address, organization).stream()
                .filter(ping -> Boolean.TRUE.equals(ping.getResult()))
                .map(RsuOnlineStatusProjection::getTimestamp)
                .findFirst()
                .orElse(null);
        return new LastOnlineDto(address.getHostAddress(), lastOnline);
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
