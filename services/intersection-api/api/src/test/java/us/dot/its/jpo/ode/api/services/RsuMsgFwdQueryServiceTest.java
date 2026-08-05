package us.dot.its.jpo.ode.api.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.models.postgres.dtos.RsuMsgFwdConfigDto;
import us.dot.its.jpo.ode.api.models.postgres.dtos.RsuMsgFwdConfigsDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdConfig;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdConfigId;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdType;
import us.dot.its.jpo.ode.api.repositories.SnmpMsgfwdConfigRepository;

@ExtendWith(MockitoExtension.class)
class RsuMsgFwdQueryServiceTest {

    @Mock
    private SnmpMsgfwdConfigRepository snmpMsgfwdConfigRepository;

    @InjectMocks
    private RsuMsgFwdQueryService rsuMsgFwdQueryService;

    private SnmpMsgfwdConfig buildConfig(String typeName, int snmpIndex, String messageType, String destIp,
            int destPort, boolean active, boolean security) throws UnknownHostException {
        SnmpMsgfwdType type = new SnmpMsgfwdType();
        type.setName(typeName);

        SnmpMsgfwdConfigId id = new SnmpMsgfwdConfigId();
        id.setSnmpIndex(snmpIndex);

        SnmpMsgfwdConfig config = new SnmpMsgfwdConfig();
        config.setId(id);
        config.setMsgfwdType(type);
        config.setMessageType(messageType);
        config.setDestIpv4(InetAddress.getByName(destIp));
        config.setDestPort(destPort);
        config.setStartDatetime(Instant.parse("2024-04-01T06:00:00Z"));
        config.setEndDatetime(Instant.parse("2034-04-01T06:00:00Z"));
        config.setActive(active);
        config.setSecurity(security);
        return config;
    }

    @Test
    void getMsgFwdConfigs_DsrcType_KeyedDirectlyBySnmpIndex() throws Exception {
        SnmpMsgfwdConfig config1 = buildConfig("rsuDsrcFwd", 1, "bsm", "10.0.0.80", 46800, true, false);
        SnmpMsgfwdConfig config2 = buildConfig("rsuDsrcFwd", 2, "bsm", "10.0.0.81", 46800, true, true);
        when(snmpMsgfwdConfigRepository.findByRsuIpv4AddressAndOrganizationName(any(), anyString()))
                .thenReturn(List.of(config1, config2));

        RsuMsgFwdConfigsDto result = rsuMsgFwdQueryService.getMsgFwdConfigs("10.0.0.80", "Test");

        Map<String, Object> rsuFwdSnmpwalk = result.getRsuFwdSnmpwalk();
        assertEquals(2, rsuFwdSnmpwalk.size());

        RsuMsgFwdConfigDto dto1 = (RsuMsgFwdConfigDto) rsuFwdSnmpwalk.get("1");
        assertEquals("BSM", dto1.getMessageType());
        assertEquals("10.0.0.80", dto1.getIp());
        assertEquals(46800, dto1.getPort());
        assertEquals("2024-04-01T00:00:00-06:00", dto1.getStartDateTime());
        assertEquals("2034-04-01T00:00:00-06:00", dto1.getEndDateTime());
        assertEquals("Enabled", dto1.getConfigActive());
        assertEquals("Disabled", dto1.getFullWsmp());

        RsuMsgFwdConfigDto dto2 = (RsuMsgFwdConfigDto) rsuFwdSnmpwalk.get("2");
        assertEquals("Enabled", dto2.getFullWsmp());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMsgFwdConfigs_ReceivedAndXmitTypes_NestedUnderTables() throws Exception {
        SnmpMsgfwdConfig received = buildConfig("rsuReceivedMsg", 1, "bsm", "10.0.0.80", 46800, true, true);
        SnmpMsgfwdConfig xmit = buildConfig("rsuXmitMsgFwding", 1, "map", "10.0.0.80", 44920, true, true);
        when(snmpMsgfwdConfigRepository.findByRsuIpv4AddressAndOrganizationName(any(), anyString()))
                .thenReturn(List.of(received, xmit));

        RsuMsgFwdConfigsDto result = rsuMsgFwdQueryService.getMsgFwdConfigs("10.0.0.80", "Test");

        Map<String, Object> rsuFwdSnmpwalk = result.getRsuFwdSnmpwalk();
        assertTrue(rsuFwdSnmpwalk.containsKey("rsuReceivedMsgTable"));
        assertTrue(rsuFwdSnmpwalk.containsKey("rsuXmitMsgFwdingTable"));

        Map<String, RsuMsgFwdConfigDto> receivedTable = (Map<String, RsuMsgFwdConfigDto>) rsuFwdSnmpwalk
                .get("rsuReceivedMsgTable");
        Map<String, RsuMsgFwdConfigDto> xmitTable = (Map<String, RsuMsgFwdConfigDto>) rsuFwdSnmpwalk
                .get("rsuXmitMsgFwdingTable");
        assertEquals("BSM", receivedTable.get("1").getMessageType());
        assertEquals("MAP", xmitTable.get("1").getMessageType());
        assertEquals(44920, xmitTable.get("1").getPort());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMsgFwdConfigs_OnlyReceivedType_XmitTableBalancedEmpty() throws Exception {
        SnmpMsgfwdConfig received = buildConfig("rsuReceivedMsg", 1, "bsm", "10.0.0.80", 46800, true, true);
        when(snmpMsgfwdConfigRepository.findByRsuIpv4AddressAndOrganizationName(any(), anyString()))
                .thenReturn(List.of(received));

        RsuMsgFwdConfigsDto result = rsuMsgFwdQueryService.getMsgFwdConfigs("10.0.0.80", "Test");

        Map<String, Object> rsuFwdSnmpwalk = result.getRsuFwdSnmpwalk();
        assertTrue(rsuFwdSnmpwalk.containsKey("rsuXmitMsgFwdingTable"));
        assertTrue(((Map<String, RsuMsgFwdConfigDto>) rsuFwdSnmpwalk.get("rsuXmitMsgFwdingTable")).isEmpty());
        assertFalse(((Map<String, RsuMsgFwdConfigDto>) rsuFwdSnmpwalk.get("rsuReceivedMsgTable")).isEmpty());
    }

    @Test
    void getMsgFwdConfigs_UnknownType_IsSkipped() throws Exception {
        SnmpMsgfwdConfig unknown = buildConfig("someUnknownType", 1, "bsm", "10.0.0.80", 46800, true, true);
        when(snmpMsgfwdConfigRepository.findByRsuIpv4AddressAndOrganizationName(any(), anyString()))
                .thenReturn(List.of(unknown));

        RsuMsgFwdConfigsDto result = rsuMsgFwdQueryService.getMsgFwdConfigs("10.0.0.80", "Test");

        assertTrue(result.getRsuFwdSnmpwalk().isEmpty());
    }

    @Test
    void getMsgFwdConfigs_InvalidIp_ThrowsBadRequest() {
        assertThrowsResponseStatusException(() -> rsuMsgFwdQueryService.getMsgFwdConfigs("not-an-ip", "Test"));
    }

    private void assertThrowsResponseStatusException(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected ResponseStatusException to be thrown");
        } catch (ResponseStatusException e) {
            // expected
        }
    }
}
