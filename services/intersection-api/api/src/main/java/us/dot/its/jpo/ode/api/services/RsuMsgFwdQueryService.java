package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.postgres.dtos.RsuMsgFwdConfigDto;
import us.dot.its.jpo.ode.api.models.postgres.dtos.RsuMsgFwdConfigsDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdConfig;
import us.dot.its.jpo.ode.api.repositories.SnmpMsgfwdConfigRepository;

@Service
@Slf4j
@RequiredArgsConstructor
public class RsuMsgFwdQueryService {

    private static final ZoneId DENVER_ZONE = ZoneId.of("America/Denver");

    private static final String DSRC_TYPE = "rsuDsrcFwd";
    private static final String RECEIVED_TYPE = "rsuReceivedMsg";
    private static final String XMIT_TYPE = "rsuXmitMsgFwding";
    private static final String RECEIVED_TABLE = "rsuReceivedMsgTable";
    private static final String XMIT_TABLE = "rsuXmitMsgFwdingTable";

    private final SnmpMsgfwdConfigRepository snmpMsgfwdConfigRepository;

    /**
     * Returns the SNMP message-forwarding configurations stored in the
     * database for the given RSU, grouped the same way as the Python
     * rsu_message_forward_helpers.format_snmp_msgfwd_configs function.
     */
    public RsuMsgFwdConfigsDto getMsgFwdConfigs(String rsuIp, String organization) {
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
            RsuMsgFwdConfigDto configDto = toConfigDto(config);
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

        if (rsuFwdSnmpwalk.containsKey(RECEIVED_TABLE) && !rsuFwdSnmpwalk.containsKey(XMIT_TABLE)) {
            rsuFwdSnmpwalk.put(XMIT_TABLE, new HashMap<String, RsuMsgFwdConfigDto>());
        } else if (rsuFwdSnmpwalk.containsKey(XMIT_TABLE) && !rsuFwdSnmpwalk.containsKey(RECEIVED_TABLE)) {
            rsuFwdSnmpwalk.put(RECEIVED_TABLE, new HashMap<String, RsuMsgFwdConfigDto>());
        }

        return new RsuMsgFwdConfigsDto(rsuFwdSnmpwalk);
    }

    @SuppressWarnings("unchecked")
    private void putInTable(Map<String, Object> rsuFwdSnmpwalk, String tableName, String snmpIndex,
            RsuMsgFwdConfigDto configDto) {
        Map<String, RsuMsgFwdConfigDto> table = (Map<String, RsuMsgFwdConfigDto>) rsuFwdSnmpwalk
                .computeIfAbsent(tableName, key -> new HashMap<String, RsuMsgFwdConfigDto>());
        table.put(snmpIndex, configDto);
    }

    private RsuMsgFwdConfigDto toConfigDto(SnmpMsgfwdConfig config) {
        return new RsuMsgFwdConfigDto(
                config.getMessageType().toUpperCase(),
                config.getDestIpv4().getHostAddress(),
                config.getDestPort(),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(config.getStartDatetime().atZone(DENVER_ZONE)),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(config.getEndDatetime().atZone(DENVER_ZONE)),
                toEnabledDisabled(config.getActive()),
                toEnabledDisabled(config.getSecurity()));
    }

    private String toEnabledDisabled(boolean value) {
        return value ? "Enabled" : "Disabled";
    }
}
