package us.dot.its.jpo.ode.api.controllers.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import us.dot.its.jpo.ode.api.controllers.advice.GlobalExceptionHandler;
import us.dot.its.jpo.ode.api.keycloak.config.KeycloakSecurityConfig;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadVerification;
import us.dot.its.jpo.ode.api.services.FirmwareUploadService;
import us.dot.its.jpo.ode.api.services.PermissionService;

@WebMvcTest(value = AdminFirmwareController.class, properties = "enable.api=true",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = KeycloakSecurityConfig.class))
@Import({ GlobalExceptionHandler.class, AdminFirmwareControllerTest.TestSecurityConfiguration.class })
@ContextConfiguration(classes = {
        AdminFirmwareController.class,
        GlobalExceptionHandler.class,
        AdminFirmwareControllerTest.TestSecurityConfiguration.class
})
class AdminFirmwareControllerTest {
    private static final String REQUEST_BODY = """
            {
              "vendor_name": "Acme",
              "model_name": "RoadRunner",
              "version": "y20.97.0",
              "file_name": "firmware.bin",
              "content_length": 12345,
              "checksum_algorithm": "CRC32C",
              "checksum": "ImIEBA==",
              "content_type": "application/octet-stream"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(name = "PermissionService")
    private PermissionService permissionService;

    @MockitoBean
    private FirmwareUploadService firmwareUploadService;

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfiguration {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                    .build();
        }
    }

    @Test
    @WithMockUser
    void adminReceivesSignedUploadResponse() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        UUID uploadId = UUID.fromString("1ef8f6f7-cae8-45cc-af92-8de58f5ffed8");
        when(firmwareUploadService.createFirmwareSignedUploadUrl(any(), eq("user")))
                .thenReturn(new FirmwareUploadUrl(uploadId, "https://storage.googleapis.com/signed", "PUT",
                "Acme/RoadRunner/y20.97.0/firmware.bin",
                Instant.parse("2026-09-02T12:15:00Z"),
                Map.of("Content-Type", "application/octet-stream", "x-goog-if-generation-match", "0")));

        mockMvc.perform(post("/admin/firmware/signed-upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upload_id").value(uploadId.toString()))
                .andExpect(jsonPath("$.upload_url").value("https://storage.googleapis.com/signed"))
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.object_name")
                        .value("Acme/RoadRunner/y20.97.0/firmware.bin"))
                .andExpect(jsonPath("$.required_headers.x-goog-if-generation-match").value("0"));
    }

    @Test
    @WithMockUser
    void nonAdminIsRejected() throws Exception {
        when(permissionService.isSuperUser()).thenReturn(false);
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(false);

        mockMvc.perform(post("/admin/firmware/signed-upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isForbidden());

        verify(firmwareUploadService, never()).createFirmwareSignedUploadUrl(any(), any());
    }

    @Test
    @WithMockUser
    void invalidFilenameIsRejected() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);

        mockMvc.perform(post("/admin/firmware/signed-upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY.replace("firmware.bin", "../firmware.bin")))
                .andExpect(status().isBadRequest());

        verify(firmwareUploadService, never()).createFirmwareSignedUploadUrl(any(), any());
    }

    @Test
    @WithMockUser
    void invalidChecksumCharactersAreRejected() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);

        mockMvc.perform(post("/admin/firmware/signed-upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY.replace("ImIEBA==", "not:hash")))
                .andExpect(status().isBadRequest());

        verify(firmwareUploadService, never()).createFirmwareSignedUploadUrl(any(), any());
    }

    @Test
    @WithMockUser
    void adminCanCompleteUploadVerification() throws Exception {
        UUID uploadId = UUID.fromString("1ef8f6f7-cae8-45cc-af92-8de58f5ffed8");
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        when(firmwareUploadService.completeFirmwareUpload(uploadId)).thenReturn(new FirmwareUploadVerification(
                uploadId, FirmwareUploadStatus.VERIFIED, "Acme/RoadRunner/y20.97.0/firmware.bin",
                12345L, "CRC32C", "ImIEBA==", "17", Instant.parse("2026-09-02T12:10:00Z")));

        mockMvc.perform(post("/admin/firmware/uploads/{uploadId}/complete", uploadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upload_id").value(uploadId.toString()))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.checksum_algorithm").value("CRC32C"))
                .andExpect(jsonPath("$.checksum").value("ImIEBA=="))
                .andExpect(jsonPath("$.provider_object_version").value("17"));
    }
}
