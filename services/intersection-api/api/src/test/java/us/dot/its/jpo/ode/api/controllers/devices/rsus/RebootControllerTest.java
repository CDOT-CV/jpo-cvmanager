package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.RsuRebootService;
import us.dot.its.jpo.ode.api.services.RsuManagementService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Import(TestcontainersConfiguration.class)
class RebootControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PermissionService permissions;
    @MockitoBean RsuRebootService service;

    @BeforeEach
    void ownerOperator() {
        when(permissions.hasRole(UserRole.OPERATOR)).thenReturn(true);
        // Match the real helper: validate the address before checking ownership.
        when(permissions.isRsuOwner(anyString(), eq("OPERATOR"))).thenAnswer(invocation -> {
            RsuManagementService.toIpv4Address(invocation.getArgument(0));
            return true;
        });
    }

    @Test
    void returnsAcceptanceWithoutWaitingForSsh() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.requestReboot(any())).thenReturn(id);
        mvc.perform(post("/devices/rsus/reboot/192.0.2.10").with(jwt()).header("Organization", "Test Org"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.requestId").value(id.toString()));
    }

    @Test
    void regularUserCannotReboot() throws Exception {
        when(permissions.hasRole(UserRole.OPERATOR)).thenReturn(false);
        mvc.perform(post("/devices/rsus/reboot/192.0.2.10").with(jwt())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void nonOwnerCannotReboot() throws Exception {
        when(permissions.isRsuOwner(anyString(), anyString())).thenReturn(false);
        mvc.perform(post("/devices/rsus/reboot/192.0.2.10").with(jwt())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "192.0.2.999", "127.1", "192.000.2.10", "192.0.2.10 "})
    void invalidAddressRejectedDuringOwnershipCheck(String address) throws Exception {
        mvc.perform(post("/devices/rsus/reboot/{ip}", address).with(jwt())).andExpect(status().isBadRequest());
        verify(permissions).isRsuOwner(address, "OPERATOR");
        verifyNoInteractions(service);
    }

    @Test
    void superUserCanRebootWithoutOperatorRole() throws Exception {
        when(permissions.isSuperUser()).thenReturn(true);
        when(permissions.hasRole(UserRole.OPERATOR)).thenReturn(false);
        when(service.requestReboot(any())).thenReturn(UUID.randomUUID());

        mvc.perform(post("/devices/rsus/reboot/192.0.2.10").with(jwt()))
                .andExpect(status().isAccepted());
        verify(permissions).isRsuOwner("192.0.2.10", "OPERATOR");
    }

    @Test
    void saturationIsNotReportedAsAccepted() throws Exception {
        when(service.requestReboot(any())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Busy"));
        mvc.perform(post("/devices/rsus/reboot/192.0.2.10").with(jwt())).andExpect(status().isServiceUnavailable());
    }
}
