package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.rsu.MsgFwdConfigDto;
import us.dot.its.jpo.ode.api.models.rsu.MsgFwdConfigsResponse;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.RsuMsgFwdQueryService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RsuMsgFwdQueryControllerTest {
    private static final String ENDPOINT = "/devices/rsus/msgfwd-query";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RsuMsgFwdQueryService rsuMsgFwdQueryService;

    @MockitoBean
    private PermissionService permissionService;

    private MsgFwdConfigsResponse buildResponse() {
        MsgFwdConfigDto config = new MsgFwdConfigDto(
                "BSM", "10.0.0.80", 46800,
                "2024-04-01T00:00:00-06:00", "2034-04-01T00:00:00-06:00",
                "Enabled", "Disabled");
        return new MsgFwdConfigsResponse(Map.of("1", config));
    }

    @Test
    void getMsgFwdConfigs_superUser_returnsLegacyShape() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(true);
        when(rsuMsgFwdQueryService.getMsgFwdConfigs("10.0.0.80", "TestOrg")).thenReturn(buildResponse());

        mockMvc.perform(get(ENDPOINT).param("rsu_ip", "10.0.0.80").header("Organization", "TestOrg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RsuFwdSnmpwalk.1['Message Type']").value("BSM"))
                .andExpect(jsonPath("$.RsuFwdSnmpwalk.1.IP").value("10.0.0.80"))
                .andExpect(jsonPath("$.RsuFwdSnmpwalk.1.Port").value(46800))
                .andExpect(jsonPath("$.RsuFwdSnmpwalk.1['Start DateTime']").value("2024-04-01T00:00:00-06:00"))
                .andExpect(jsonPath("$.RsuFwdSnmpwalk.1['End DateTime']").value("2034-04-01T00:00:00-06:00"))
                .andExpect(jsonPath("$.RsuFwdSnmpwalk.1['Config Active']").value("Enabled"))
                .andExpect(jsonPath("$.RsuFwdSnmpwalk.1['Full WSMP']").value("Disabled"));
    }

    @Test
    void getMsgFwdConfigs_orgMemberWithRsuAccess_returnsOk() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(false);
        when(permissionService.hasRoleInOrg("TestOrg", "USER")).thenReturn(true);
        when(permissionService.hasRsu("10.0.0.80", "USER")).thenReturn(true);
        when(rsuMsgFwdQueryService.getMsgFwdConfigs("10.0.0.80", "TestOrg")).thenReturn(buildResponse());

        mockMvc.perform(get(ENDPOINT).param("rsu_ip", "10.0.0.80").header("Organization", "TestOrg"))
                .andExpect(status().isOk());
    }

    @Test
    void getMsgFwdConfigs_rejectsUnauthorizedOrganization() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(false);
        when(permissionService.hasRoleInOrg("OtherOrg", "USER")).thenReturn(false);
        when(permissionService.hasRsu("10.0.0.80", "USER")).thenReturn(true);

        mockMvc.perform(get(ENDPOINT).param("rsu_ip", "10.0.0.80").header("Organization", "OtherOrg"))
                .andExpect(status().isForbidden());

        verify(rsuMsgFwdQueryService, never()).getMsgFwdConfigs(anyString(), anyString());
    }

    @Test
    void getMsgFwdConfigs_rejectsUnauthorizedRsu() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(false);
        when(permissionService.hasRoleInOrg("TestOrg", "USER")).thenReturn(true);
        when(permissionService.hasRsu("10.0.0.80", "USER")).thenReturn(false);

        mockMvc.perform(get(ENDPOINT).param("rsu_ip", "10.0.0.80").header("Organization", "TestOrg"))
                .andExpect(status().isForbidden());

        verify(rsuMsgFwdQueryService, never()).getMsgFwdConfigs(anyString(), anyString());
    }

    @Test
    void getMsgFwdConfigs_invalidIp_returnsBadRequest() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(true);
        when(rsuMsgFwdQueryService.getMsgFwdConfigs("not-an-ip", "TestOrg"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "IP address must be IPv4"));

        mockMvc.perform(get(ENDPOINT).param("rsu_ip", "not-an-ip").header("Organization", "TestOrg"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMsgFwdConfigs_missingRsuIpParam_returnsBadRequest() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(true);

        mockMvc.perform(get(ENDPOINT).header("Organization", "TestOrg"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMsgFwdConfigs_missingOrganizationHeader_returnsBadRequest() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(true);

        mockMvc.perform(get(ENDPOINT).param("rsu_ip", "10.0.0.80"))
                .andExpect(status().isBadRequest());
    }
}
