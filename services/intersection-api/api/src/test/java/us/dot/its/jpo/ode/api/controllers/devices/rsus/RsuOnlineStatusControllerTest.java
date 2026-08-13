package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import us.dot.its.jpo.ode.api.models.rsu.LastOnlineDto;
import us.dot.its.jpo.ode.api.models.rsu.OnlineStatusDto;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.RsuOnlineStatusService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RsuOnlineStatusControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RsuOnlineStatusService rsuOnlineStatusService;

    @MockitoBean
    private PermissionService permissionService;

    @Test
    void getAllOnlineStatuses_returnsOnlineStatusByIp() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(false);
        when(permissionService.hasRoleInOrg("TestOrg", "USER")).thenReturn(true);
        when(rsuOnlineStatusService.getOnlineStatuses("TestOrg"))
                .thenReturn(Map.of("10.0.0.1", new OnlineStatusDto("online")));

        mockMvc.perform(get("/devices/rsus/online-status").header("Organization", "TestOrg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlineStatusByIp['10.0.0.1'].current_status").value("online"));
    }

    @Test
    void getLastOnline_returnsIsoTimestampAndNullWhenNoPingExists() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(false);
        when(permissionService.hasRoleInOrg("TestOrg", "USER")).thenReturn(true);
        when(rsuOnlineStatusService.getLastOnline("TestOrg", "10.0.0.1"))
                .thenReturn(new LastOnlineDto("10.0.0.1", Instant.parse("2026-08-03T12:00:00Z")));

        mockMvc.perform(get("/devices/rsus/online-status/10.0.0.1").header("Organization", "TestOrg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ip").value("10.0.0.1"))
                .andExpect(jsonPath("$.last_online").value("2026-08-03T12:00:00Z"));

        when(rsuOnlineStatusService.getLastOnline("TestOrg", "10.0.0.2"))
                .thenReturn(new LastOnlineDto("10.0.0.2", null));
        mockMvc.perform(get("/devices/rsus/online-status/10.0.0.2").header("Organization", "TestOrg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.last_online").value(nullValue()));
    }

    @Test
    void getAllOnlineStatuses_rejectsUnauthorizedOrganization() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(false);
        when(permissionService.hasRoleInOrg("OtherOrg", "USER")).thenReturn(false);

        mockMvc.perform(get("/devices/rsus/online-status").header("Organization", "OtherOrg"))
                .andExpect(status().isForbidden());

        verify(rsuOnlineStatusService, never()).getOnlineStatuses(anyString());
    }
}
