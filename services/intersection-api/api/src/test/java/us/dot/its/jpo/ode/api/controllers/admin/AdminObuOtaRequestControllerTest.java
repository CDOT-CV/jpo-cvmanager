package us.dot.its.jpo.ode.api.controllers.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.storage.ObuOtaRequestPage;
import us.dot.its.jpo.ode.api.services.ObuOtaRequestService;
import us.dot.its.jpo.ode.api.services.ObuOtaRequestService.Filters;
import us.dot.its.jpo.ode.api.services.PermissionService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "firmware-upload.cleanup.enabled=false")
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminObuOtaRequestControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(name = "PermissionService")
    private PermissionService permissionService;

    @MockitoBean
    private ObuOtaRequestService obuOtaRequestService;

    @Test
    @WithMockUser
    void nonAdminsCannotListRequests() throws Exception {
        mockMvc.perform(get("/admin/obu-ota-requests")).andExpect(status().isForbidden());
        verifyNoInteractions(obuOtaRequestService);
    }

    @Test
    @WithMockUser
    void adminsListRequestsWithFiltersInSnakeCase() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        var item = new ObuOtaRequestPage.Item(7, "OBU-1", Instant.parse("2026-06-01T12:30:00Z"), "10.1.2.3",
                "y20.47.0", "ob4-generic-ro-y20.48.2.tar.sig", "error", "404: Firmware not found", "Commsignia");
        when(obuOtaRequestService.search(anyInt(), anyInt(), any(), any()))
                .thenReturn(new ObuOtaRequestPage(List.of(item), 1));

        mockMvc.perform(get("/admin/obu-ota-requests")
                .param("page", "1")
                .param("size", "25")
                .param("serial", "OBU")
                .param("manufacturer", "Commsignia")
                .param("status", "error")
                .param("start", "2026-06-01T00:00:00Z")
                .param("end", "2026-06-02T00:00:00Z")
                .param("sort", "obu_sn,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.items[0].request_id").value(7))
                .andExpect(jsonPath("$.items[0].obu_sn").value("OBU-1"))
                .andExpect(jsonPath("$.items[0].request_datetime").value("2026-06-01T12:30:00Z"))
                .andExpect(jsonPath("$.items[0].origin_ip").value("10.1.2.3"))
                .andExpect(jsonPath("$.items[0].requested_firmware_version").value("ob4-generic-ro-y20.48.2.tar.sig"))
                .andExpect(jsonPath("$.items[0].status").value("error"))
                .andExpect(jsonPath("$.items[0].manufacturer").value("Commsignia"));

        verify(obuOtaRequestService).search(1, 25, new Filters("OBU", "Commsignia", "error",
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-02T00:00:00Z")), "obu_sn,asc");
    }

    @Test
    @WithMockUser
    void usesDefaultPagingAndSort() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        when(obuOtaRequestService.search(anyInt(), anyInt(), any(), anyString()))
                .thenReturn(new ObuOtaRequestPage(List.of(), 0));

        mockMvc.perform(get("/admin/obu-ota-requests")).andExpect(status().isOk());

        verify(obuOtaRequestService).search(eq(0), eq(100), eq(Filters.none()), eq("request_datetime,desc"));
    }

    @Test
    @WithMockUser
    void invalidRequestsReturnBadRequest() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        when(obuOtaRequestService.search(anyInt(), anyInt(), any(), any()))
                .thenThrow(new IllegalArgumentException("status must be success or error"));

        mockMvc.perform(get("/admin/obu-ota-requests").param("status", "maybe"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/obu-ota-requests").param("start", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for parameter 'start'"));
    }
}
