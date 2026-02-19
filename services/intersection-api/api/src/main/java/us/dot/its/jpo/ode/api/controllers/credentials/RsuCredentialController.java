package us.dot.its.jpo.ode.api.controllers.credentials;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.persistence.EntityNotFoundException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import us.dot.its.jpo.ode.api.mappers.RsuCredentialMapper;
import us.dot.its.jpo.ode.api.models.credentials.RsuCredentialDTO;
import us.dot.its.jpo.ode.api.services.RsuCredentialManagementService;

@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@ApiResponses(value = {
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
})
@RequestMapping("/credentials/rsu")
@RequiredArgsConstructor
public class RsuCredentialController {
    private final RsuCredentialManagementService rsuCredentialManagementService;
    private final RsuCredentialMapper rsuCredentialMapper;

    @PostMapping("/create")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRoleInOrg(#organization, 'ADMIN')")
    public RsuCredentialDTO createRsuCredential(
            @RequestHeader(name = "Organization") String organization,
            @RequestBody RsuCredentialCreateRequest rsuCredentialCreateRequest) throws EntityNotFoundException, RsuCredentialManagementService.RsuCredentialAlreadyExistsException {
        return rsuCredentialMapper.toDto(rsuCredentialManagementService.create(organization, rsuCredentialCreateRequest));
    }

    @GetMapping("/get-by-nickname")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRoleInOrg(#organization, 'ADMIN')")
    public RsuCredentialDTO getByNickname(
            @RequestHeader(name = "Organization", required = true) String organization,
            RsuCredentialGetRequest rsuCredentialGetRequest) throws EntityNotFoundException {
        return rsuCredentialMapper.toDto(rsuCredentialManagementService.getByNickname(organization, rsuCredentialGetRequest.getNickname()));
    }

    @PostMapping("/update")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRoleInOrg(#organization, 'ADMIN')")
    public RsuCredentialDTO update(
            @RequestHeader(name = "Organization", required = true) String organization,
            @RequestBody RsuCredentialPatch rsuCredentialPatch) throws EntityNotFoundException {
        return rsuCredentialMapper.toDto(rsuCredentialManagementService.update(organization, rsuCredentialPatch));
    }

    @PostMapping("/delete")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRoleInOrg(#organization, 'ADMIN')")
    public void deleteByNickname(
            @RequestHeader(name = "Organization", required = true) String organization,
            @RequestBody RsuCredentialDeleteRequest rsuCredentialDeleteRequest) {
        rsuCredentialManagementService.deleteByNickname(organization, rsuCredentialDeleteRequest.getNickname());
    }

    // requests
    @Data
    public static class RsuCredentialCreateRequest {
        private final String nickname;
        private final String username;
        private final String password;
        private final String organization;
    }

    @Data
    public static class RsuCredentialGetRequest {
        private final String nickname;
    }

    @Data
    public static class RsuCredentialPatch {
        private final String nickname;
        private String username;
        private String password;
        private String organization;
    }

    @Data
    public static class RsuCredentialDeleteRequest {
        private final String nickname;
    }
}
