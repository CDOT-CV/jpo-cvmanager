package us.dot.its.jpo.ode.api.controllers.admin;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadUrlRequest;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadVerification;
import us.dot.its.jpo.ode.api.services.FirmwareUploadService;

@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true")
@RequestMapping("/admin/firmware")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin Firmware", description = "Manage firmware uploads")
public class AdminFirmwareController {
    private final FirmwareUploadService firmwareUploadService;

    @Operation(summary = "Create a signed firmware upload URL")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Signed upload URL created"),
            @ApiResponse(responseCode = "400", description = "Invalid upload request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "Vendor/model pair was not found"),
            @ApiResponse(responseCode = "409", description = "Firmware object already exists"),
            @ApiResponse(responseCode = "502", description = "Object storage signing failed"),
            @ApiResponse(responseCode = "503", description = "Object storage is not configured")
    })
    @PostMapping(value = "/signed-upload-url", consumes = "application/json", produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('ADMIN')")
    public FirmwareUploadUrl createFirmwareSignedUploadUrl(
            @Validated @RequestBody FirmwareUploadUrlRequest request,
            Authentication authentication) {
        log.info("POST /admin/firmware/signed-upload-url. vendor={}, model={}, version={}, file={}",
                request.getVendorName(), request.getModelName(), request.getVersion(), request.getFileName());
        return firmwareUploadService.createFirmwareSignedUploadUrl(request, authentication.getName());
    }

    @Operation(summary = "Verify a completed firmware upload")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Upload checksum and size verified"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "Upload was not found"),
            @ApiResponse(responseCode = "409", description = "Object is missing or does not match the upload intent"),
            @ApiResponse(responseCode = "502", description = "Object storage metadata lookup failed"),
            @ApiResponse(responseCode = "503", description = "Object storage is not configured")
    })
    @PostMapping(value = "/uploads/{uploadId}/complete", produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('ADMIN')")
    public FirmwareUploadVerification completeFirmwareUpload(@PathVariable UUID uploadId) {
        log.info("POST /admin/firmware/uploads/{}/complete", uploadId);
        return firmwareUploadService.completeFirmwareUpload(uploadId);
    }
}
