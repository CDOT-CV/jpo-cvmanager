package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdConfig;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdConfigId;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpMsgfwdConfigRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpMsgfwdTypeRepository;
import us.dot.its.jpo.snmp.client.SnmpTarget;
import us.dot.its.jpo.snmp.rsu.ntcip1218.ForwardingRow;
import us.dot.its.jpo.snmp.rsu.ntcip1218.ForwardingTable;
import us.dot.its.jpo.snmp.rsu.ntcip1218.MessageType;

/**
 * Stores a complete device readback as the current PostgreSQL forwarding
 * snapshot for one RSU.
 *
 * Persistence is separate from SNMP execution so that a
 * database rollback cannot undo a device change. Callers therefore retain
 * verified device outcomes even when snapshot synchronization fails.
 */
@Service
@RequiredArgsConstructor
public class RsuMessageForwardingSnapshotService {
    private final RsuRepository rsuRepository;
    private final SnmpMsgfwdConfigRepository configRepository;
    private final SnmpMsgfwdTypeRepository typeRepository;

    /**
     * Replaces both forwarding tables atomically. The caller must supply a
     * complete, successful two-table read.
     *
     * An empty list means the device has no rows, not that retrieval failed.
     * Runtime failures roll back deletion and insertion together, preserving the
     * previous snapshot. Forwarding MIB timestamps are always interpreted as UTC.
     */
    @Transactional(timeout = 10)
    public void replace(Integer rsuId, String rsuIp, List<ForwardingRow> rows) {
        var receive = typeRepository.findByName("rsuReceivedMsg").orElseThrow();
        var transmit = typeRepository.findByName("rsuXmitMsgFwding").orElseThrow();
        var rsu = rsuRepository.getReferenceById(rsuId);
        // Resolve table identities and validate every mapped row before deleting stored
        // state.
        var configs = rows.stream().map(row -> {
            var type = row.table() == ForwardingTable.RECEIVE ? receive : transmit;
            var id = new SnmpMsgfwdConfigId();
            id.setRsuId(rsuId);
            id.setMsgfwdType(type.getId());
            id.setSnmpIndex(row.index());
            var config = new SnmpMsgfwdConfig();
            config.setId(id);
            config.setRsu(rsu);
            config.setMsgfwdType(type);
            config.setMessageType(Arrays.stream(MessageType.values()).filter(m -> m.psid().equals(row.psid()))
                    .map(Enum::name).findFirst().orElse("Other"));
            config.setDestIpv4(address(row.destination()));
            config.setDestPort(row.port());
            // The eight-octet MIB dates omit an offset but explicitly represent UTC.
            config.setStartDatetime(row.start().toInstant(ZoneOffset.UTC));
            config.setEndDatetime(row.stop().toInstant(ZoneOffset.UTC));
            config.setActive(row.status() == 1);
            config.setSecurity(row.includeSecurity());
            return config;
        }).toList();
        // Keep replacement inside this transaction; a failed flush must also roll back
        // the delete.
        configRepository.removeSnmpMsgfwdConfigByIpv4Address(address(rsuIp));
        configRepository.saveAllAndFlush(configs);
    }

    private InetAddress address(String value) {
        SnmpTarget.validateIpv4(value);
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IPv4 address");
        }
    }
}
