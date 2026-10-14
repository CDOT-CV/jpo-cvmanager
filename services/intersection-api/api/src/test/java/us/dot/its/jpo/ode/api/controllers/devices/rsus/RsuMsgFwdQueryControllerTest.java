package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.rsu.MsgFwdConfigDto;
import us.dot.its.jpo.ode.api.models.rsu.MsgFwdConfigsResponse;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.RsuMsgFwdQueryService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RsuMsgFwdQueryControllerTest {
    private static final String URL = "/devices/rsus/msgfwd-query";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RsuMsgFwdQueryService rsuMsgFwdQueryService;

    @MockitoBean
    private PermissionService permissionService;

    private static MsgFwdConfigsResponse buildResponse() {
        MsgFwdConfigDto config = new MsgFwdConfigDto(
                "BSM", "10.0.0.80", 46800,
                "2024-04-01T00:00:00-06:00", "2034-04-01T00:00:00-06:00",
                "Enabled", "Disabled");
        return new MsgFwdConfigsResponse(Map.of("1", config));
    }

    @Test
    void getMsgFwdConfigs_superUser_returnsPythonShapedBody() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(true);
        when(rsuMsgFwdQueryService.getMsgFwdConfigs("10.0.0.80", "TestOrg")).thenReturn(buildResponse());

        mockMvc.perform(get(URL).with(jwt()).param("rsu_ip", "10.0.0.80").header("Organization", "TestOrg"))
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
    void getMsgFwdConfigs_hasRsuAndUserRole_returnsOk() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(false);
        when(permissionService.hasRsu("10.0.0.80", "USER")).thenReturn(true);
        when(permissionService.hasRole(UserRole.USER)).thenReturn(true);
        when(rsuMsgFwdQueryService.getMsgFwdConfigs("10.0.0.80", "TestOrg")).thenReturn(buildResponse());

        mockMvc.perform(get(URL).with(jwt()).param("rsu_ip", "10.0.0.80").header("Organization", "TestOrg"))
                .andExpect(status().isOk());
    }

    @Test
    void getMsgFwdConfigs_rsuNotInHeaderOrganization_returnsEmptyConfigs() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(false);
        when(permissionService.hasRsu("10.0.0.80", "USER")).thenReturn(true);
        when(permissionService.hasRole(UserRole.USER)).thenReturn(true);
        when(rsuMsgFwdQueryService.getMsgFwdConfigs("10.0.0.80", "OtherOrg"))
                .thenReturn(new MsgFwdConfigsResponse(Map.of()));

        mockMvc.perform(get(URL).with(jwt()).param("rsu_ip", "10.0.0.80").header("Organization", "OtherOrg"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"RsuFwdSnmpwalk\":{}}", true));
    }

    @Test
    void getMsgFwdConfigs_missingRsuIpParam_returnsBadRequest() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(true);

        mockMvc.perform(get(URL).with(jwt()).header("Organization", "TestOrg"))
                .andExpect(status().isBadRequest());

        verify(rsuMsgFwdQueryService, never()).getMsgFwdConfigs(anyString(), anyString());
    }

    @Test
    void getMsgFwdConfigs_missingOrganizationHeader_returnsForbidden() throws Exception {
        // Matches the Python middleware's organization_required check, which applies to super users too
        when(permissionService.isSuperUser()).thenReturn(true);

        mockMvc.perform(get(URL).with(jwt()).param("rsu_ip", "10.0.0.80"))
                .andExpect(status().isForbidden());

        verify(rsuMsgFwdQueryService, never()).getMsgFwdConfigs(anyString(), anyString());
    }

    @Test
    void getMsgFwdConfigs_noRsuAccess_returnsForbidden() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(false);
        when(permissionService.hasRsu(anyString(), anyString())).thenReturn(false);
        when(permissionService.hasRole(UserRole.USER)).thenReturn(true);

        mockMvc.perform(get(URL).with(jwt()).param("rsu_ip", "10.0.0.80").header("Organization", "TestOrg"))
                .andExpect(status().isForbidden());

        verify(rsuMsgFwdQueryService, never()).getMsgFwdConfigs(anyString(), anyString());
    }

    @Test
    void getMsgFwdConfigs_noUserRoleInOrganization_returnsForbidden() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(false);
        when(permissionService.hasRsu("10.0.0.80", "USER")).thenReturn(true);
        when(permissionService.hasRole(UserRole.USER)).thenReturn(false);

        mockMvc.perform(get(URL).with(jwt()).param("rsu_ip", "10.0.0.80").header("Organization", "TestOrg"))
                .andExpect(status().isForbidden());

        verify(rsuMsgFwdQueryService, never()).getMsgFwdConfigs(anyString(), anyString());
    }

    @Test
    void getMsgFwdConfigs_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get(URL).param("rsu_ip", "10.0.0.80").header("Organization", "TestOrg"))
                .andExpect(status().isForbidden());

        verify(rsuMsgFwdQueryService, never()).getMsgFwdConfigs(anyString(), anyString());
    }
}
