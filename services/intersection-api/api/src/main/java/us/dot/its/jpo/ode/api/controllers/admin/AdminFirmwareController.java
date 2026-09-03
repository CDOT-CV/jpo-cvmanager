package us.dot.its.jpo.ode.api.controllers.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrlRequest;
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
            @ApiResponse(responseCode = "502", description = "Object storage signing failed"),
            @ApiResponse(responseCode = "503", description = "Object storage is not configured")
    })
    @PostMapping(value = "/signed-upload-url", consumes = "application/json", produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('ADMIN')")
    public SignedUploadUrl createFirmwareSignedUploadUrl(@Validated @RequestBody SignedUploadUrlRequest request) {
        log.info("POST /admin/firmware/signed-upload-url. vendor={}, model={}, version={}, file={}",
                request.getVendorName(), request.getModelName(), request.getVersion(), request.getFileName());
        return firmwareUploadService.createFirmwareSignedUploadUrl(request);
    }
}
