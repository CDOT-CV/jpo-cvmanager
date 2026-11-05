package us.dot.its.jpo.ode.api.controllers.devices;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.devices.management.RsuFirmwareOption;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.RsuManagementService;

@SpringBootTest(properties = "firmware-upload.cleanup.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
class RsuFirmwareSelectionControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private PermissionService permissions;
    @MockitoBean private RsuManagementService rsus;

    @Test
    void operatorCanFetchModelSpecificOptionsButUsersCannot() throws Exception {
        mvc.perform(get("/devices/rsus/firmware-options").with(jwt()).param("model", "Commsignia ITS-RS4-M"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rsus);
        when(permissions.hasRole(UserRole.OPERATOR)).thenReturn(true);
        when(rsus.getFirmwareOptions("Commsignia ITS-RS4-M"))
                .thenReturn(List.of(new RsuFirmwareOption(5, "v1", true)));
        mvc.perform(get("/devices/rsus/firmware-options").with(jwt()).param("model", "Commsignia ITS-RS4-M"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].firmware_id").value(5))
                .andExpect(jsonPath("$[0].version").value("v1")).andExpect(jsonPath("$[0].legacy").value(true));
    }

    @Test
    void creationRequiresAPositiveFirmwareIdAndBindsItToTheRequest() throws Exception {
        when(permissions.hasRole(UserRole.OPERATOR)).thenReturn(true);
        when(permissions.hasRoleInOrgNames(eq(UserRole.OPERATOR), anyList())).thenReturn(true);
        String body = """
                {"ip":"192.0.2.1","geo_position":{"latitude":39.7,"longitude":-104.9},
                 "milepost":1,"primary_route":"I-25","serial_number":"SN-1","scms_id":"id",
                 "model":"Commsignia ITS-RS4-M","organizations":["Org"],"tim_deposit":false,"snmp_monitoring":false
                """;
        for (String suffix : List.of("}", ",\"firmware_id\":null}", ",\"firmware_id\":0}", ",\"firmware_id\":-1}")) {
            mvc.perform(post("/devices/rsus").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body + suffix))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/devices/rsus").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("\"ip\":\"192.0.2.1\",", "") + ",\"firmware_id\":5}"))
                .andExpect(status().isBadRequest());
        verify(rsus, never()).createRsu(any(), anyList());
        mvc.perform(post("/devices/rsus").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content(body + ",\"firmware_id\":5}"))
                .andExpect(status().isCreated());
        verify(rsus).createRsu(argThat(request -> request.getFirmwareId() == 5), eq(List.of("Org")));
    }
}
