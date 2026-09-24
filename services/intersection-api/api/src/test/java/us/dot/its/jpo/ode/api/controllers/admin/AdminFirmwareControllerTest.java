package us.dot.its.jpo.ode.api.controllers.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import us.dot.its.jpo.ode.api.TestcontainersConfiguration;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.models.storage.FirmwareObjectPage;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadOptions;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadOptions.ManufacturerOption;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadOptions.ModelOption;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadVerification;
import us.dot.its.jpo.ode.api.services.FirmwareObjectService;
import us.dot.its.jpo.ode.api.services.FirmwareRuleService;
import us.dot.its.jpo.ode.api.services.FirmwareRuleService.FirmwareRuleConflictException;
import us.dot.its.jpo.ode.api.models.storage.FirmwareRuleModels.*;
import us.dot.its.jpo.ode.api.services.FirmwareDeletionService;
import us.dot.its.jpo.ode.api.services.FirmwareDeletionService.FirmwareDeletionConflictException;
import us.dot.its.jpo.ode.api.services.FirmwareUploadOptionsService;
import us.dot.its.jpo.ode.api.services.FirmwareUploadService;
import us.dot.its.jpo.ode.api.services.FirmwareUploadService.FirmwareUploadConfigurationException;
import us.dot.its.jpo.ode.api.services.PermissionService;
import us.dot.its.jpo.ode.api.services.FirmwareUploadService.FirmwareUploadVerificationException;
import us.dot.its.jpo.ode.api.services.FirmwareUploadService.FirmwareVersionAlreadyExistsException;
import us.dot.its.jpo.ode.api.storage.ObjectStorageUnavailableException;
import us.dot.its.jpo.ode.api.storage.ObjectStorageService.ObjectStorageConflictException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "firmware-upload.cleanup.enabled=false")
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
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

    @MockitoBean
    private FirmwareObjectService firmwareObjectService;

    @MockitoBean
    private FirmwareUploadOptionsService firmwareUploadOptionsService;

    @MockitoBean
    private FirmwareDeletionService firmwareDeletionService;

    @MockitoBean
    private FirmwareRuleService firmwareRuleService;

    @Test
    @WithMockUser
    void onlyAdminsCanReadOrModifyUpgradePaths() throws Exception {
        mockMvc.perform(get("/admin/firmware/upgrade-rules")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/firmware/images/3/upgrade-rules")).andExpect(status().isForbidden());
        mockMvc.perform(put("/admin/firmware/images/3/upgrade-rules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sources\":[{\"source_id\":1,\"expected_target_id\":2}]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/firmware/upgrade-rules/1").param("expected_target_id", "2"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(firmwareRuleService);
    }

    @Test
    @WithMockUser
    void adminCanAssignPathsAndInvalidSelectionsAreRejected() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        mockMvc.perform(put("/admin/firmware/images/3/upgrade-rules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sources\":[{\"source_id\":1,\"expected_target_id\":2}]}"))
                .andExpect(status().isNoContent());
        verify(firmwareRuleService).assign(eq(3), eq(new Assignments(List.of(new Assignment(1, 2)))));
        for (var body : List.of("{}", "{\"sources\":[]}", "{\"sources\":[null]}", "{\"sources\":[{\"source_id\":-1}]}")) {
            mockMvc.perform(put("/admin/firmware/images/3/upgrade-rules").contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    @WithMockUser
    void ruleConflictsHaveAnActionable409Response() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        doThrow(new FirmwareRuleConflictException("Upgrade paths changed"))
                .when(firmwareRuleService).assign(eq(3), any());
        mockMvc.perform(put("/admin/firmware/images/3/upgrade-rules").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sources\":[{\"source_id\":1}]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Upgrade paths changed"));
    }

    @Test
    @WithMockUser
    void rulesExposeVersionNamesAndDeletionRequiresTheDisplayedDestination() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        var source = new Image(1, "Acme", "RoadRunner", "v1", true);
        var target = new Image(2, "Acme", "RoadRunner", "v2", false);
        var rule = new Rule(4, source, target, false);
        when(firmwareRuleService.list()).thenReturn(List.of(rule));
        mockMvc.perform(get("/admin/firmware/upgrade-rules")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source.version").value("v1"))
                .andExpect(jsonPath("$[0].destination.version").value("v2"))
                .andExpect(jsonPath("$[0].legacy_destination").value(false));
        mockMvc.perform(delete("/admin/firmware/upgrade-rules/4")).andExpect(status().isBadRequest());
        mockMvc.perform(delete("/admin/firmware/upgrade-rules/4").param("expected_target_id", "2"))
                .andExpect(status().isNoContent());
        verify(firmwareRuleService).delete(4, 2);
    }

    @Test
    @WithMockUser
    void adminCanDeleteTheSelectedObjectVersion() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        mockMvc.perform(delete(
                        "/admin/firmware/objects/object-id").param("provider_object_version", "17"))
                .andExpect(status().isNoContent());
        verify(firmwareDeletionService).delete("object-id", "17");
    }

    @Test
    @WithMockUser
    void rejectsDeletionForNonAdmins() throws Exception {
        mockMvc.perform(delete(
                        "/admin/firmware/objects/object-id").param("provider_object_version", "17"))
                .andExpect(status().isForbidden());
        verify(firmwareDeletionService, never()).delete(any(), any());
    }

    @Test
    @WithMockUser
    void adminCanCleanUpMissingFileRecords() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        mockMvc.perform(delete("/admin/firmware/objects/object-id/records"))
                .andExpect(status().isNoContent());
        verify(firmwareDeletionService).cleanupMissingObject("object-id");
    }

    @Test
    @WithMockUser
    void rejectsMissingFileCleanupForNonAdmins() throws Exception {
        mockMvc.perform(delete("/admin/firmware/objects/object-id/records"))
                .andExpect(status().isForbidden());
        verify(firmwareDeletionService, never()).cleanupMissingObject(any());
    }

    @Test
    @WithMockUser
    void cleanupReportsAReappearedFileAsAConflict() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        doThrow(new FirmwareDeletionConflictException("The firmware file is present in storage. Refresh the table."))
                .when(firmwareDeletionService).cleanupMissingObject("object-id");
        mockMvc.perform(delete("/admin/firmware/objects/object-id/records").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The firmware file is present in storage. Refresh the table."));
    }

    @Test
    @WithMockUser
    void deletionRequiresObjectVersion() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        mockMvc.perform(delete(
                        "/admin/firmware/objects/object-id"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete(
                        "/admin/firmware/objects/object-id").param("provider_object_version", " "))
                .andExpect(status().isBadRequest());
        verify(firmwareDeletionService, never()).delete(any(), any());
    }

    @Test
    @WithMockUser
    void deletionConflictsAndProviderFailuresHaveUsefulResponses() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        doThrow(new FirmwareDeletionConflictException("Firmware is in use"))
                .doThrow(new ObjectStorageConflictException("Refresh the firmware table"))
                .doThrow(new ObjectStorageUnavailableException("Storage is unavailable"))
                .when(firmwareDeletionService).delete("object-id", "17");

        mockMvc.perform(delete("/admin/firmware/objects/object-id")
                        .accept(MediaType.APPLICATION_JSON).param("provider_object_version", "17"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Firmware is in use"));
        mockMvc.perform(delete("/admin/firmware/objects/object-id")
                        .accept(MediaType.APPLICATION_JSON).param("provider_object_version", "17"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Refresh the firmware table"));
        mockMvc.perform(delete("/admin/firmware/objects/object-id")
                        .accept(MediaType.APPLICATION_JSON).param("provider_object_version", "17"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value("Storage is unavailable"));
    }

    @Test
    @WithMockUser
    void listsStructuredUploadOptionsForAdmins() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        when(firmwareUploadOptionsService.getOptions()).thenReturn(new FirmwareUploadOptions(List.of(
                new ManufacturerOption(1, "Commsignia", ".tar.sig", List.of(
                        new ModelOption(10, "ITS-RS4-M"),
                        new ModelOption(11, "ITS-RS4-S"))))));

        mockMvc.perform(get("/admin/firmware/upload-options").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manufacturers[0].manufacturer_id").value(1))
                .andExpect(jsonPath("$.manufacturers[0].name").value("Commsignia"))
                .andExpect(jsonPath("$.manufacturers[0].file_extension").value(".tar.sig"))
                .andExpect(jsonPath("$.manufacturers[0].models[1].model_id").value(11))
                .andExpect(jsonPath("$.manufacturers[0].models[1].name").value("ITS-RS4-S"));
    }

    @Test
    @WithMockUser
    void rejectsUploadOptionsForNonAdmins() throws Exception {
        mockMvc.perform(get("/admin/firmware/upload-options").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(firmwareUploadOptionsService, never()).getOptions();
    }

    @Test
    @WithMockUser
    void listsObjectsForAdmins() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        when(firmwareObjectService.list(1, 25, "Acme", "version", "model,desc")).thenReturn(
                new FirmwareObjectPage("gcp", List.of(
                        new FirmwareObjectPage.Item("object-id", "Acme/model/version/file.bin",
                                "Acme", "model", "version", "file.bin", 42L,
                                Instant.parse("2026-09-10T18:00:00Z"), "1", null, null, null,
                                "UNTRACKED")), 26));
        mockMvc.perform(get("/admin/firmware/objects")
                .param("size", "25")
                .param("page", "1")
                .param("manufacturer", "Acme")
                .param("search", "version")
                .param("sort", "model,desc")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects[0].manufacturer").value("Acme"))
                .andExpect(jsonPath("$.objects[0].model").value("model"))
                .andExpect(jsonPath("$.objects[0].updated_at").value("2026-09-10T18:00:00Z"))
                .andExpect(jsonPath("$.total_elements").value(26))
                .andExpect(jsonPath("$.container").doesNotExist());
        verify(firmwareObjectService).list(1, 25, "Acme", "version", "model,desc");
    }

    @Test
    @WithMockUser
    void rejectsObjectListingForNonAdmins() throws Exception {
        mockMvc.perform(get("/admin/firmware/objects")
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden());
        verify(firmwareObjectService, never()).list(anyInt(), anyInt(), any(), any(), any());
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
    void unavailableStorageReturnsServiceUnavailable() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        when(firmwareUploadService.createFirmwareSignedUploadUrl(any(), eq("user")))
                .thenThrow(new ObjectStorageUnavailableException("Object storage provider is not configured"));

        mockMvc.perform(post("/admin/firmware/signed-upload-url")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("Object storage provider is not configured"));
    }

    @Test
    @WithMockUser
    void existingFirmwareReturnsConflict() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        String message = "Firmware already exists for this manufacturer, model, and version";
        when(firmwareUploadService.createFirmwareSignedUploadUrl(any(), eq("user")))
                .thenThrow(new FirmwareVersionAlreadyExistsException(message));

        mockMvc.perform(post("/admin/firmware/signed-upload-url")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(message));
    }

    @Test
    @WithMockUser
    void missingManufacturerExtensionReturnsServiceUnavailable() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        String message = "Firmware uploads are not configured for manufacturer 'Kapsch'";
        when(firmwareUploadService.createFirmwareSignedUploadUrl(any(), eq("user")))
                .thenThrow(new FirmwareUploadConfigurationException(message));

        mockMvc.perform(post("/admin/firmware/signed-upload-url")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value(message));
    }

    @Test
    @WithMockUser
    void verificationFailureReturnsConflict() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);
        UUID uploadId = UUID.randomUUID();
        String message = "Cannot verify this upload because the expected firmware file was not found in storage. "
                + "Ensure the file upload using the signed URL completed successfully before requesting verification.";
        when(firmwareUploadService.completeFirmwareUpload(uploadId))
                .thenThrow(new FirmwareUploadVerificationException(message));

        mockMvc.perform(post("/admin/firmware/uploads/{uploadId}/complete", uploadId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(message));
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
    void versionContainingSpacesIsRejected() throws Exception {
        when(permissionService.hasRole(UserRole.ADMIN)).thenReturn(true);

        mockMvc.perform(post("/admin/firmware/signed-upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY.replace("y20.97.0", "y20 97 0")))
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
                12345L, "CRC32C", "ImIEBA==", "17", Instant.parse("2026-09-02T12:10:00Z"), 12));

        mockMvc.perform(post("/admin/firmware/uploads/{uploadId}/complete", uploadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upload_id").value(uploadId.toString()))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.firmware_id").value(12))
                .andExpect(jsonPath("$.checksum_algorithm").value("CRC32C"))
                .andExpect(jsonPath("$.checksum").value("ImIEBA=="))
                .andExpect(jsonPath("$.provider_object_version").value("17"));
    }
}
