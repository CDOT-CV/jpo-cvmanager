package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.postgres.dtos.RsuMsgFwdConfigDto;
import us.dot.its.jpo.ode.api.models.postgres.dtos.RsuMsgFwdConfigsDto;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.RsuMsgFwdQueryService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
class MsgFwdQueryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RsuMsgFwdQueryService rsuMsgFwdQueryService;

    @MockitoBean
    PermissionService permissionService;

    private RsuMsgFwdConfigsDto buildResponse() {
        RsuMsgFwdConfigDto config = new RsuMsgFwdConfigDto(
                "BSM", "10.0.0.80", 46800,
                "2024-04-01T00:00:00-06:00", "2034-04-01T00:00:00-06:00",
                "Enabled", "Disabled");
        return new RsuMsgFwdConfigsDto(Map.of("1", config));
    }

    @Test
    void getMsgFwdConfigs_SuperUser_ReturnsOk() throws Exception {
        given(permissionService.isSuperUser()).willReturn(true);
        given(rsuMsgFwdQueryService.getMsgFwdConfigs("10.0.0.80", "TestOrg")).willReturn(buildResponse());

        mockMvc.perform(get("/devices/rsus/msgfwd-query")
                .with(jwt())
                .param("rsu_ip", "10.0.0.80")
                .header("Organization", "TestOrg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RsuFwdSnmpwalk.1['Message Type']").value("BSM"))
                .andExpect(jsonPath("$.RsuFwdSnmpwalk.1.IP").value("10.0.0.80"))
                .andExpect(jsonPath("$.RsuFwdSnmpwalk.1.Port").value(46800))
                .andExpect(jsonPath("$.RsuFwdSnmpwalk.1['Config Active']").value("Enabled"))
                .andExpect(jsonPath("$.RsuFwdSnmpwalk.1['Full WSMP']").value("Disabled"));
    }

    @Test
    void getMsgFwdConfigs_HasRsuAndUserRole_ReturnsOk() throws Exception {
        given(permissionService.isSuperUser()).willReturn(false);
        given(permissionService.hasRsu("10.0.0.80", "USER")).willReturn(true);
        given(permissionService.hasRole(UserRole.USER)).willReturn(true);
        given(rsuMsgFwdQueryService.getMsgFwdConfigs("10.0.0.80", "TestOrg")).willReturn(buildResponse());

        mockMvc.perform(get("/devices/rsus/msgfwd-query")
                .with(jwt())
                .param("rsu_ip", "10.0.0.80")
                .header("Organization", "TestOrg"))
                .andExpect(status().isOk());
    }

    @Test
    void getMsgFwdConfigs_MissingRsuIpParam_ReturnsBadRequest() throws Exception {
        given(permissionService.isSuperUser()).willReturn(true);

        mockMvc.perform(get("/devices/rsus/msgfwd-query")
                .with(jwt())
                .header("Organization", "TestOrg"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMsgFwdConfigs_MissingOrganizationHeader_ReturnsBadRequest() throws Exception {
        given(permissionService.isSuperUser()).willReturn(true);

        mockMvc.perform(get("/devices/rsus/msgfwd-query")
                .with(jwt())
                .param("rsu_ip", "10.0.0.80"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMsgFwdConfigs_NoRsuAccess_ReturnsForbidden() throws Exception {
        given(permissionService.isSuperUser()).willReturn(false);
        given(permissionService.hasRsu(anyString(), anyString())).willReturn(false);

        mockMvc.perform(get("/devices/rsus/msgfwd-query")
                .with(jwt())
                .param("rsu_ip", "10.0.0.80")
                .header("Organization", "TestOrg"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMsgFwdConfigs_Unauthenticated_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/devices/rsus/msgfwd-query")
                .param("rsu_ip", "10.0.0.80")
                .header("Organization", "TestOrg"))
                .andExpect(status().isForbidden());
    }
}
