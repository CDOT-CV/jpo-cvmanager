package us.dot.its.jpo.ode.api.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdConfig;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdConfigId;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdType;
import us.dot.its.jpo.ode.api.models.rsu.MsgFwdConfigDto;
import us.dot.its.jpo.ode.api.models.rsu.MsgFwdConfigsResponse;
import us.dot.its.jpo.ode.api.repositories.SnmpMsgfwdConfigRepository;

@ExtendWith(MockitoExtension.class)
class RsuMsgFwdQueryServiceTest {
    @Mock
    private SnmpMsgfwdConfigRepository snmpMsgfwdConfigRepository;

    private RsuMsgFwdQueryService service;

    @BeforeEach
    void setUp() {
        service = new RsuMsgFwdQueryService(snmpMsgfwdConfigRepository, "America/Denver");
    }

    @Test
    void getMsgFwdConfigs_dsrcType_keyedDirectlyBySnmpIndex() throws Exception {
        when(snmpMsgfwdConfigRepository.findByRsuIpv4AddressAndOrganizationName(
                eq(InetAddress.getByName("10.0.0.80")), eq("TestOrg"))).thenReturn(List.of(
                        config("rsuDsrcFwd", 1, "bsm", "10.0.0.80", 46800, true, false),
                        config("rsuDsrcFwd", 2, "bsm", "10.0.0.81", 46800, true, true)));

        Map<String, Object> result = service.getMsgFwdConfigs("10.0.0.80", "TestOrg").getRsuFwdSnmpwalk();

        assertEquals(2, result.size());
        MsgFwdConfigDto first = (MsgFwdConfigDto) result.get("1");
        assertEquals("BSM", first.getMessageType());
        assertEquals("10.0.0.80", first.getIp());
        assertEquals(46800, first.getPort());
        assertEquals("2024-04-01T00:00:00-06:00", first.getStartDateTime());
        assertEquals("2034-04-01T00:00:00-06:00", first.getEndDateTime());
        assertEquals("Enabled", first.getConfigActive());
        assertEquals("Disabled", first.getFullWsmp());
        assertEquals("Enabled", ((MsgFwdConfigDto) result.get("2")).getFullWsmp());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMsgFwdConfigs_receivedAndXmitTypes_nestedUnderTables() throws Exception {
        when(snmpMsgfwdConfigRepository.findByRsuIpv4AddressAndOrganizationName(any(), anyString())).thenReturn(List.of(
                config("rsuReceivedMsg", 1, "bsm", "10.0.0.80", 46800, true, true),
                config("rsuXmitMsgFwding", 1, "map", "10.0.0.80", 44920, true, true)));

        Map<String, Object> result = service.getMsgFwdConfigs("10.0.0.80", "TestOrg").getRsuFwdSnmpwalk();

        Map<String, MsgFwdConfigDto> received = (Map<String, MsgFwdConfigDto>) result.get("rsuReceivedMsgTable");
        Map<String, MsgFwdConfigDto> xmit = (Map<String, MsgFwdConfigDto>) result.get("rsuXmitMsgFwdingTable");
        assertEquals("BSM", received.get("1").getMessageType());
        assertEquals("MAP", xmit.get("1").getMessageType());
        assertEquals(44920, xmit.get("1").getPort());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMsgFwdConfigs_onlyReceivedType_addsEmptyXmitTable() throws Exception {
        when(snmpMsgfwdConfigRepository.findByRsuIpv4AddressAndOrganizationName(any(), anyString())).thenReturn(List.of(
                config("rsuReceivedMsg", 1, "bsm", "10.0.0.80", 46800, true, true)));

        Map<String, Object> result = service.getMsgFwdConfigs("10.0.0.80", "TestOrg").getRsuFwdSnmpwalk();

        assertTrue(((Map<String, MsgFwdConfigDto>) result.get("rsuXmitMsgFwdingTable")).isEmpty());
        assertFalse(((Map<String, MsgFwdConfigDto>) result.get("rsuReceivedMsgTable")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMsgFwdConfigs_onlyXmitType_addsEmptyReceivedTable() throws Exception {
        when(snmpMsgfwdConfigRepository.findByRsuIpv4AddressAndOrganizationName(any(), anyString())).thenReturn(List.of(
                config("rsuXmitMsgFwding", 1, "map", "10.0.0.80", 44920, true, true)));

        Map<String, Object> result = service.getMsgFwdConfigs("10.0.0.80", "TestOrg").getRsuFwdSnmpwalk();

        assertTrue(((Map<String, MsgFwdConfigDto>) result.get("rsuReceivedMsgTable")).isEmpty());
        assertFalse(((Map<String, MsgFwdConfigDto>) result.get("rsuXmitMsgFwdingTable")).isEmpty());
    }

    @Test
    void getMsgFwdConfigs_unknownType_isSkipped() throws Exception {
        when(snmpMsgfwdConfigRepository.findByRsuIpv4AddressAndOrganizationName(any(), anyString())).thenReturn(List.of(
                config("someUnknownType", 1, "bsm", "10.0.0.80", 46800, true, true)));

        assertTrue(service.getMsgFwdConfigs("10.0.0.80", "TestOrg").getRsuFwdSnmpwalk().isEmpty());
    }

    @Test
    void getMsgFwdConfigs_noConfigsInOrganization_returnsEmptyMap() {
        when(snmpMsgfwdConfigRepository.findByRsuIpv4AddressAndOrganizationName(any(), anyString()))
                .thenReturn(List.of());

        MsgFwdConfigsResponse result = service.getMsgFwdConfigs("10.0.0.80", "OtherOrg");

        assertTrue(result.getRsuFwdSnmpwalk().isEmpty());
    }

    @Test
    void getMsgFwdConfigs_configuredTimezone_formatsTimestampsInThatZone() throws Exception {
        service = new RsuMsgFwdQueryService(snmpMsgfwdConfigRepository, "UTC");
        when(snmpMsgfwdConfigRepository.findByRsuIpv4AddressAndOrganizationName(any(), anyString())).thenReturn(List.of(
                config("rsuDsrcFwd", 1, "bsm", "10.0.0.80", 46800, true, false)));

        MsgFwdConfigDto dto = (MsgFwdConfigDto) service.getMsgFwdConfigs("10.0.0.80", "TestOrg")
                .getRsuFwdSnmpwalk().get("1");

        assertEquals("2024-04-01T06:00:00Z", dto.getStartDateTime());
    }

    @Test
    void constructor_blankTimezone_fallsBackToDenver() throws Exception {
        service = new RsuMsgFwdQueryService(snmpMsgfwdConfigRepository, " ");
        when(snmpMsgfwdConfigRepository.findByRsuIpv4AddressAndOrganizationName(any(), anyString())).thenReturn(List.of(
                config("rsuDsrcFwd", 1, "bsm", "10.0.0.80", 46800, true, false)));

        MsgFwdConfigDto dto = (MsgFwdConfigDto) service.getMsgFwdConfigs("10.0.0.80", "TestOrg")
                .getRsuFwdSnmpwalk().get("1");

        assertEquals("2024-04-01T00:00:00-06:00", dto.getStartDateTime());
    }

    @Test
    void getMsgFwdConfigs_invalidIp_throwsBadRequest() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.getMsgFwdConfigs("[not-an-ip", "TestOrg"));

        assertEquals(400, exception.getStatusCode().value());
    }

    private static SnmpMsgfwdConfig config(String typeName, int snmpIndex, String messageType, String destIp,
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
}
