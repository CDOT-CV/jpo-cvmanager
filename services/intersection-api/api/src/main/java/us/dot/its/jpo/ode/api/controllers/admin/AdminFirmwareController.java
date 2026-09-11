package us.dot.its.jpo.ode.api.controllers.admin;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.storage.FirmwareObjectPage;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadOptions;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadUrlRequest;
import us.dot.its.jpo.ode.api.models.storage.FirmwareUploadVerification;
import us.dot.its.jpo.ode.api.services.FirmwareObjectService;
import us.dot.its.jpo.ode.api.services.FirmwareUploadOptionsService;
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
    private final FirmwareObjectService firmwareObjectService;
    private final FirmwareUploadOptionsService firmwareUploadOptionsService;

    @Operation(summary = "List valid manufacturer and model options for firmware uploads")
    @GetMapping(value = "/upload-options", produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('ADMIN')")
    public FirmwareUploadOptions getUploadOptions() {
        return firmwareUploadOptionsService.getOptions();
    }

    @Operation(summary = "List firmware objects and their verification state")
    @GetMapping(value = "/objects", produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('ADMIN')")
    public FirmwareObjectPage listObjects(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size,
            @RequestParam(name = "manufacturer", required = false) String manufacturer,
            @RequestParam(name = "search", required = false) String search) {
        return firmwareObjectService.list(page, size, manufacturer, search);
    }

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
