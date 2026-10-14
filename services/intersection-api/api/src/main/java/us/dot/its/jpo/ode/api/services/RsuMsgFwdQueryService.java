package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdConfig;
import us.dot.its.jpo.ode.api.models.rsu.MsgFwdConfigDto;
import us.dot.its.jpo.ode.api.models.rsu.MsgFwdConfigsResponse;
import us.dot.its.jpo.ode.api.repositories.SnmpMsgfwdConfigRepository;

@Service
@Slf4j
public class RsuMsgFwdQueryService {
    static final String DEFAULT_TIMEZONE = "America/Denver";

    private static final String DSRC_TYPE = "rsuDsrcFwd";
    private static final String RECEIVED_TYPE = "rsuReceivedMsg";
    private static final String XMIT_TYPE = "rsuXmitMsgFwding";
    private static final String RECEIVED_TABLE = "rsuReceivedMsgTable";
    private static final String XMIT_TABLE = "rsuXmitMsgFwdingTable";

    private final SnmpMsgfwdConfigRepository snmpMsgfwdConfigRepository;
    private final ZoneId zoneId;

    public RsuMsgFwdQueryService(SnmpMsgfwdConfigRepository snmpMsgfwdConfigRepository,
            @Value("${cvmanager.timezone:" + DEFAULT_TIMEZONE + "}") String timezone) {
        this.snmpMsgfwdConfigRepository = snmpMsgfwdConfigRepository;
        this.zoneId = ZoneId.of(timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone);
    }

    @Transactional(readOnly = true)
    public MsgFwdConfigsResponse getMsgFwdConfigs(String rsuIp, String organization) {
        InetAddress ipv4Address;
        try {
            ipv4Address = InetAddress.getByName(rsuIp);
        } catch (UnknownHostException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid RSU IP address: " + rsuIp, e);
        }

        List<SnmpMsgfwdConfig> configs = snmpMsgfwdConfigRepository
                .findByRsuIpv4AddressAndOrganizationName(ipv4Address, organization);

        Map<String, Object> rsuFwdSnmpwalk = new HashMap<>();
        for (SnmpMsgfwdConfig config : configs) {
            String msgfwdType = config.getMsgfwdType().getName();
            MsgFwdConfigDto configDto = toConfigDto(config);
            String snmpIndex = String.valueOf(config.getId().getSnmpIndex());

            if (msgfwdType.equalsIgnoreCase(DSRC_TYPE)) {
                rsuFwdSnmpwalk.put(snmpIndex, configDto);
            } else if (msgfwdType.equalsIgnoreCase(RECEIVED_TYPE)) {
                putInTable(rsuFwdSnmpwalk, RECEIVED_TABLE, snmpIndex, configDto);
            } else if (msgfwdType.equalsIgnoreCase(XMIT_TYPE)) {
                putInTable(rsuFwdSnmpwalk, XMIT_TABLE, snmpIndex, configDto);
            } else {
                log.warn("Encountered unknown message forwarding configuration type '{}' for RSU '{}'",
                        msgfwdType, rsuIp);
            }
        }

        // Make sure both RX and TX objects are available if the RSU has NTCIP 1218 configurations
        if (rsuFwdSnmpwalk.containsKey(RECEIVED_TABLE) && !rsuFwdSnmpwalk.containsKey(XMIT_TABLE)) {
            rsuFwdSnmpwalk.put(XMIT_TABLE, new HashMap<String, MsgFwdConfigDto>());
        } else if (rsuFwdSnmpwalk.containsKey(XMIT_TABLE) && !rsuFwdSnmpwalk.containsKey(RECEIVED_TABLE)) {
            rsuFwdSnmpwalk.put(RECEIVED_TABLE, new HashMap<String, MsgFwdConfigDto>());
        }

        return new MsgFwdConfigsResponse(rsuFwdSnmpwalk);
    }

    @SuppressWarnings("unchecked")
    private static void putInTable(Map<String, Object> rsuFwdSnmpwalk, String tableName, String snmpIndex,
            MsgFwdConfigDto configDto) {
        Map<String, MsgFwdConfigDto> table = (Map<String, MsgFwdConfigDto>) rsuFwdSnmpwalk
                .computeIfAbsent(tableName, key -> new HashMap<String, MsgFwdConfigDto>());
        table.put(snmpIndex, configDto);
    }

    private MsgFwdConfigDto toConfigDto(SnmpMsgfwdConfig config) {
        return new MsgFwdConfigDto(
                config.getMessageType().toUpperCase(),
                config.getDestIpv4().getHostAddress(),
                config.getDestPort(),
                formatTimestamp(config.getStartDatetime()),
                formatTimestamp(config.getEndDatetime()),
                toEnabledDisabled(config.getActive()),
                toEnabledDisabled(config.getSecurity()));
    }

    private String formatTimestamp(Instant timestamp) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(timestamp.atZone(zoneId));
    }

    private static String toEnabledDisabled(boolean value) {
        return value ? "Enabled" : "Disabled";
    }
}
