package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import us.dot.its.jpo.ode.api.config.SnmpProperties;
import us.dot.its.jpo.ode.api.models.devices.management.MessageForwardingConfiguration;
import us.dot.its.jpo.ode.api.models.devices.management.MessageForwardingConfiguration.Table;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpMsgfwdConfigRepository;
import us.dot.its.jpo.snmp.client.SnmpTarget;
import us.dot.its.jpo.snmp.execution.*;
import us.dot.its.jpo.snmp.rsu.ntcip1218.*;

/**
 * Supplies cached and live forwarding views using the same row format. Cached reads need no
 * device credentials or connectivity. Live reads share command admission limits and per-device
 * serialization, and never replace the database snapshot as a side effect of viewing a device.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enable.api", havingValue = "true")
public class RsuMessageForwardingReadService {
    private final RsuRepository rsuRepository;
    private final SnmpMsgfwdConfigRepository configRepository;
    private final RsuMessageForwardingContextService contextService;
    private final SnmpTaskExecutor executor;
    private final SnmpProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MessageForwardingConfiguration> cached(String rsuIp) {
        var address = address(rsuIp);
        if (rsuRepository.findByIpv4Address(address) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RSU not found: " + rsuIp);
        }
        var displayZone = ZoneId.of(properties.getDisplayTimeZone());
        return configRepository.findForwardingByIpv4Address(address).stream().map(row -> {
            var table = switch (row.getMsgfwdType().getName()) {
                case "rsuReceivedMsg" -> Table.RECEIVE;
                case "rsuXmitMsgFwding" -> Table.TRANSMIT;
                case "rsuDsrcFwd" -> Table.DSRC;
                default -> throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unknown stored forwarding table");
            };
            return new MessageForwardingConfiguration(table, row.getId().getSnmpIndex(),
                    row.getMessageType().toUpperCase(Locale.ROOT), row.getDestIpv4().getHostAddress(), row.getDestPort(),
                    row.getStartDatetime().atZone(displayZone).toOffsetDateTime(),
                    row.getEndDatetime().atZone(displayZone).toOffsetDateTime(),
                    Boolean.TRUE.equals(row.getActive()), Boolean.TRUE.equals(row.getSecurity()));
        }).toList();
    }

    /** Returns only a complete successful read; timeouts/errors trigger the UI's cached-data fallback. */
    public List<MessageForwardingConfiguration> live(String rsuIp) {
        validateAddress(rsuIp);
        var device = contextService.load(List.of(rsuIp)).getFirst().device();
        var read = new SnmpStep<>("read-current-forwarding", SnmpStep.Kind.READ,
                ctx -> new Ntcip1218Client(ctx.client(), clock).readForwarding(device));
        var task = new SnmpTask("read-message-forwarding", device.target(), List.of(read));
        var handle = executor.submit(task, properties.taskOptions());
        SnmpTaskResult result;
        try {
            result = handle.await();
        } catch (InterruptedException e) {
            handle.cancel();
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Live forwarding read cancelled");
        }
        if (result.status() != SnmpTaskResult.Status.SUCCEEDED) {
            var status = switch (result.status()) {
                case TIMED_OUT -> HttpStatus.GATEWAY_TIMEOUT;
                case REJECTED, QUEUE_EXPIRED, CANCELLED -> HttpStatus.SERVICE_UNAVAILABLE;
                default -> HttpStatus.BAD_GATEWAY;
            };
            throw new ResponseStatusException(status, result.summary().message());
        }
        return result.value(read).stream().map(this::liveConfiguration).toList();
    }

    private MessageForwardingConfiguration liveConfiguration(ForwardingRow row) {
        var displayZone = ZoneId.of(properties.getDisplayTimeZone());
        var messageType = Arrays.stream(MessageType.values()).filter(type -> type.psid().equals(row.psid()))
                .map(Enum::name).findFirst().orElse("OTHER");
        return new MessageForwardingConfiguration(Table.valueOf(row.table().name()), row.index(), messageType,
                row.destination(), row.port(), row.start().atZone(ZoneOffset.UTC).withZoneSameInstant(displayZone).toOffsetDateTime(),
                row.stop().atZone(ZoneOffset.UTC).withZoneSameInstant(displayZone).toOffsetDateTime(),
                row.status() == 1, row.includeSecurity());
    }

    /** Validate before the permission helper or repository can interpret the input as a DNS name. */
    public static void validateAddress(String value) {
        try {
            SnmpTarget.validateIpv4(value);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected an RSU IPv4 address");
        }
    }

    private InetAddress address(String value) {
        validateAddress(value);
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid RSU IPv4 address");
        }
    }
}
