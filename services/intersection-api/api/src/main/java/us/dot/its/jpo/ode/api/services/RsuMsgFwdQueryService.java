package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdConfig;
import us.dot.its.jpo.ode.api.models.rsu.MsgFwdConfigDto;
import us.dot.its.jpo.ode.api.models.rsu.MsgFwdConfigsResponse;
import us.dot.its.jpo.ode.api.repositories.SnmpMsgfwdConfigRepository;
import us.dot.its.jpo.ode.api.utils.IpAddressUtils;

@Service
@Slf4j
public class RsuMsgFwdQueryService {

    private static final String DSRC_TYPE = "rsuDsrcFwd";
    private static final String RECEIVED_TYPE = "rsuReceivedMsg";
    private static final String XMIT_TYPE = "rsuXmitMsgFwding";
    private static final String RECEIVED_TABLE = "rsuReceivedMsgTable";
    private static final String XMIT_TABLE = "rsuXmitMsgFwdingTable";

    private static final DateTimeFormatter WHOLE_SECONDS_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ssxxx");
    private static final DateTimeFormatter MICROSECONDS_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSxxx");

    private final SnmpMsgfwdConfigRepository snmpMsgfwdConfigRepository;
    private final ZoneId zoneId;

    public RsuMsgFwdQueryService(SnmpMsgfwdConfigRepository snmpMsgfwdConfigRepository,
            @Value("${cvmanager.timezone}") String timezone) {
        this.snmpMsgfwdConfigRepository = snmpMsgfwdConfigRepository;
        this.zoneId = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public MsgFwdConfigsResponse getMsgFwdConfigs(String rsuIp, String organization) {
        InetAddress ipv4Address = IpAddressUtils.toIpv4Address(rsuIp);

        List<SnmpMsgfwdConfig> configs = snmpMsgfwdConfigRepository
                .findByRsuIpv4AddressAndOrganizationName(ipv4Address, organization);

        Map<String, MsgFwdConfigDto> dsrcConfigs = new HashMap<>();
        Map<String, MsgFwdConfigDto> receivedConfigs = new HashMap<>();
        Map<String, MsgFwdConfigDto> xmitConfigs = new HashMap<>();
        for (SnmpMsgfwdConfig config : configs) {
            String msgfwdType = config.getMsgfwdType().getName();
            String snmpIndex = String.valueOf(config.getId().getSnmpIndex());

            if (msgfwdType.equalsIgnoreCase(DSRC_TYPE)) {
                dsrcConfigs.put(snmpIndex, toConfigDto(config));
            } else if (msgfwdType.equalsIgnoreCase(RECEIVED_TYPE)) {
                receivedConfigs.put(snmpIndex, toConfigDto(config));
            } else if (msgfwdType.equalsIgnoreCase(XMIT_TYPE)) {
                xmitConfigs.put(snmpIndex, toConfigDto(config));
            } else {
                log.warn("Encountered unknown message forwarding configuration type '{}' for RSU '{}'",
                        msgfwdType, ipv4Address.getHostAddress());
            }
        }

        Map<String, Object> rsuFwdSnmpwalk = new HashMap<>(dsrcConfigs);
        // Make sure both RX and TX tables are present if the RSU has any NTCIP-1218 configurations
        if (!receivedConfigs.isEmpty() || !xmitConfigs.isEmpty()) {
            rsuFwdSnmpwalk.put(RECEIVED_TABLE, receivedConfigs);
            rsuFwdSnmpwalk.put(XMIT_TABLE, xmitConfigs);
        }

        return new MsgFwdConfigsResponse(rsuFwdSnmpwalk);
    }

    private MsgFwdConfigDto toConfigDto(SnmpMsgfwdConfig config) {
        return new MsgFwdConfigDto(
                config.getMessageType().toUpperCase(),
                config.getDestIpv4().getHostAddress(),
                config.getDestPort(),
                formatDateTime(config.getStartDatetime()),
                formatDateTime(config.getEndDatetime()),
                toEnabledDisabled(config.getActive()),
                toEnabledDisabled(config.getSecurity()));
    }

    private String formatDateTime(Instant instant) {
        ZonedDateTime zoned = instant.atZone(zoneId);
        return zoned.getNano() / 1_000 == 0
                ? WHOLE_SECONDS_FORMAT.format(zoned)
                : MICROSECONDS_FORMAT.format(zoned);
    }

    private String toEnabledDisabled(boolean value) {
        return value ? "Enabled" : "Disabled";
    }
}
