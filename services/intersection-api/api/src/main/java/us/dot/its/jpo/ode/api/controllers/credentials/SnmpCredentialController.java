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
import us.dot.its.jpo.ode.api.mappers.SnmpCredentialMapper;
import us.dot.its.jpo.ode.api.models.credentials.SnmpCredentialDTO;
import us.dot.its.jpo.ode.api.services.SnmpCredentialManagementService;

@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true")
@ApiResponses(value = {
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
})
@RequestMapping("/credentials/snmp")
@RequiredArgsConstructor
public class SnmpCredentialController {
    private final SnmpCredentialManagementService snmpCredentialManagementService;
    private final SnmpCredentialMapper snmpCredentialMapper;

    @PostMapping("/create")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRoleInOrg(#organization, 'ADMIN')")
    public SnmpCredentialDTO createSnmpCredential(
            @RequestHeader(name = "Organization", required = true) String organization,
            @RequestBody SnmpCredentialCreateRequest request) throws SnmpCredentialManagementService.SnmpCredentialAlreadyExistsException, EntityNotFoundException {
        return snmpCredentialMapper.toDto(snmpCredentialManagementService.create(organization, request));
    }

    @GetMapping("/get-by-nickname")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRoleInOrg(#organization, 'ADMIN')")
    public SnmpCredentialDTO getByNickname(
            @RequestHeader(name = "Organization", required = true) String organization,
            SnmpCredentialGetRequest request) throws EntityNotFoundException {
        return snmpCredentialMapper.toDto(snmpCredentialManagementService.getByNickname(organization, request.getNickname()));
    }

    @PostMapping("/update")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRoleInOrg(#organization, 'ADMIN')")
    public SnmpCredentialDTO update(
            @RequestHeader(name = "Organization", required = true) String organization,
            @RequestBody SnmpCredentialPatch snmpCredentialPatch) throws EntityNotFoundException {
        return snmpCredentialMapper.toDto(snmpCredentialManagementService.update(organization, snmpCredentialPatch));
    }

    @PostMapping("/delete")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRoleInOrg(#organization, 'ADMIN')")
    public void deleteByNickname(
            @RequestHeader(name = "Organization", required = true) String organization,
            @RequestBody SnmpCredentialDeleteRequest request) {
        snmpCredentialManagementService.deleteByNickname(organization, request.getNickname());
    }

    // requests
    @Data
    public static class SnmpCredentialCreateRequest {
        private final String nickname;
        private final String username;
        private final String password;
        private final String organization;
    }

    @Data
    public static class SnmpCredentialGetRequest {
        private final String nickname;
    }

    @Data
    public static class SnmpCredentialPatch {
        private final String nickname;
        private String username;
        private String password;
        private String organization;
    }

    @Data
    public static class SnmpCredentialDeleteRequest {
        private final String nickname;
    }
}
