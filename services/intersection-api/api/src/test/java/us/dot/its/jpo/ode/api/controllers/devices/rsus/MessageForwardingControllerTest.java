package us.dot.its.jpo.ode.api.controllers.devices.rsus;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.RsuMessageForwardingService;
import us.dot.its.jpo.ode.api.services.RsuMessageForwardingReadService;
import us.dot.its.jpo.ode.api.models.devices.management.MessageForwardingResult;
import us.dot.its.jpo.snmp.execution.SnmpTaskResult;
import us.dot.its.jpo.snmp.execution.SnmpTaskSummary;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MessageForwardingController.class, properties = "enable.api=true")
@ContextConfiguration(classes = { MessageForwardingController.class, MessageForwardingControllerTest.Security.class,
        us.dot.its.jpo.ode.api.controllers.advice.GlobalExceptionHandler.class })
class MessageForwardingControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean RsuMessageForwardingService forwardingService;
    @MockitoBean RsuMessageForwardingReadService readService;
    @MockitoBean(name = "PermissionService") PermissionService permissionService;

    private static final String PATH = "/devices/rsus/message-forwarding";
    private static final String BODY = """
            {"rsu_ips":["192.0.2.10"],"message_type":"MAP","destination_ip":"198.51.100.3","include_security":true}
            """;

    @EnableMethodSecurity
    static class Security {
        @Bean SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(r -> r.anyRequest().authenticated()).build();
        }
    }

    @BeforeEach void authorizeOperator() {
        when(permissionService.hasRole(UserRole.OPERATOR)).thenReturn(true);
        when(permissionService.findUnauthorizedRsus(anyList(), eq("OPERATOR"))).thenReturn(List.of());
    }

    @Test void createAndDeleteRouteToSameController() throws Exception {
        var result = new MessageForwardingResult(new SnmpTaskSummary("fixture-task", "udp:192.0.2.10:161",
                SnmpTaskResult.Status.SUCCEEDED, "Change verified.", null, 25, List.of()),
                SnmpTaskResult.WriteState.VERIFIED, true, MessageForwardingResult.DatabaseSync.UPDATED);
        when(forwardingService.create(any())).thenReturn(Map.of("192.0.2.10", result));
        when(forwardingService.delete(any())).thenReturn(Map.of());
        mvc.perform(post(PATH).with(user("operator")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['192.0.2.10'].execution.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$['192.0.2.10'].writeState").value("VERIFIED"))
                .andExpect(jsonPath("$['192.0.2.10'].databaseSync").value("UPDATED"));
        mvc.perform(delete(PATH).with(user("operator")).contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace(",\"include_security\":true", ""))).andExpect(status().isOk());
        verify(forwardingService).create(argThat(r -> r.getIncludeSecurity() && r.getDestinationIp().equals("198.51.100.3")));
        verify(forwardingService).delete(any());
    }

    @Test void anonymousCallerCannotSendCommands() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(forwardingService);
    }

    @Test void unauthorizedMemberRejectsEntireBatchForBothOperations() throws Exception {
        when(permissionService.findUnauthorizedRsus(anyList(), anyString())).thenReturn(List.of("192.0.2.10"));
        mvc.perform(post(PATH).with(user("operator")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(delete(PATH).with(user("operator")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(forwardingService);
    }

    @Test void viewerCannotSendCommands() throws Exception {
        when(permissionService.hasRole(UserRole.OPERATOR)).thenReturn(false);
        mvc.perform(post(PATH).with(user("viewer")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(delete(PATH).with(user("viewer")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(forwardingService);
    }

    @Test void viewerCanReadCachedAndLiveForwardingForOwnedRsu() throws Exception {
        when(permissionService.hasRole(UserRole.USER)).thenReturn(true);
        when(permissionService.findUnauthorizedRsus(anyList(), eq("USER"))).thenReturn(List.of());
        var date = java.time.OffsetDateTime.parse("2026-10-15T10:00:00-06:00");
        var row = new us.dot.its.jpo.ode.api.models.devices.management.MessageForwardingConfiguration(
                us.dot.its.jpo.ode.api.models.devices.management.MessageForwardingConfiguration.Table.TRANSMIT,
                1, "MAP", "198.51.100.3", 44920, date, date.plusYears(10), true, false);
        when(readService.cached("192.0.2.10")).thenReturn(List.of(row));
        when(readService.live("192.0.2.10")).thenReturn(List.of(row));
        for (var suffix : List.of("/192.0.2.10", "/192.0.2.10/live")) {
            mvc.perform(get(PATH + suffix).with(user("viewer")).header("Organization", "Org 1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].table").value("TRANSMIT"))
                    .andExpect(jsonPath("$[0].destinationPort").value(44920))
                    .andExpect(jsonPath("$[0].startDatetime").value("2026-10-15T10:00:00-06:00"))
                    .andExpect(jsonPath("$[0].includeSecurity").value(false));
        }
        verifyNoInteractions(forwardingService);
    }

    @Test void bothReadPathsRejectForeignRsuAndNonliteralAddressesBeforeReading() throws Exception {
        when(permissionService.hasRole(UserRole.USER)).thenReturn(true);
        when(permissionService.findUnauthorizedRsus(anyList(), eq("USER"))).thenReturn(List.of("192.0.2.10"));
        for (var suffix : List.of("/192.0.2.10", "/192.0.2.10/live")) {
            mvc.perform(get(PATH + suffix).with(user("viewer"))).andExpect(status().isForbidden());
        }
        mvc.perform(get(PATH + "/localhost/live").with(user("viewer"))).andExpect(status().isBadRequest());
        verifyNoInteractions(readService);
    }

    @Test void liveTimeoutIsAnHttpErrorSoTheWebappCanFallBackToCache() throws Exception {
        when(permissionService.hasRole(UserRole.USER)).thenReturn(true);
        when(permissionService.findUnauthorizedRsus(anyList(), eq("USER"))).thenReturn(List.of());
        when(readService.live("192.0.2.10")).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.GATEWAY_TIMEOUT, "Device did not respond."));
        mvc.perform(get(PATH + "/192.0.2.10/live").with(user("viewer"))).andExpect(status().isGatewayTimeout());
    }

    @Test void liveAuthenticationFailureReturnsProblemDetails() throws Exception {
        when(permissionService.hasRole(UserRole.USER)).thenReturn(true);
        when(permissionService.findUnauthorizedRsus(anyList(), eq("USER"))).thenReturn(List.of());
        when(readService.live("192.0.2.10")).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "SNMP authentication failed."));
        for (var accept : List.of(MediaType.ALL, MediaType.APPLICATION_JSON)) {
            mvc.perform(get(PATH + "/192.0.2.10/live").with(user("viewer")).accept(accept))
                    .andExpect(status().isBadGateway())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(502))
                    .andExpect(jsonPath("$.detail").value("SNMP authentication failed."));
        }
    }

    @Test void rejectsMissingSecurityInvalidMessageTypeAndNonliteralAddresses() throws Exception {
        for (var body : List.of(BODY.replace(",\"include_security\":true", ""),
                BODY.replace("MAP", "UNKNOWN"), BODY.replace("192.0.2.10", "localhost"),
                BODY.replace("198.51.100.3", "999.1.2.3"), BODY.replace("[\"192.0.2.10\"]", "[]"))) {
            mvc.perform(post(PATH).with(user("operator")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(forwardingService);
        verify(permissionService, never()).findUnauthorizedRsus(anyList(), anyString());
    }
}
