package us.dot.its.jpo.ode.api.controllers.credentials;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import us.dot.its.jpo.ode.api.mappers.SnmpCredentialMapper;
import us.dot.its.jpo.ode.api.models.credentials.SnmpCredentialDTO;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;
import us.dot.its.jpo.ode.api.services.SnmpCredentialManagementService;

import java.util.Optional;

@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
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
    public SnmpCredentialCreateResponse createSnmpCredential(SnmpCredentialCreateRequest request) {
        SnmpCredential snmpCredential;
        try {
            snmpCredential = snmpCredentialManagementService.create(request);
        } catch(SnmpCredentialManagementService.SnmpCredentialAlreadyExistsException e) {
            return new SnmpCredentialCreateResponse(false, Optional.empty(), Optional.of("SNMP Credential already exists"));
        } catch(SnmpCredentialManagementService.OrganizationNotFoundException e) {
            return new SnmpCredentialCreateResponse(false, Optional.empty(), Optional.of("Organization not found"));
        }
        return new SnmpCredentialCreateResponse(true, Optional.of(snmpCredentialMapper.toDto(snmpCredential)), Optional.empty());
    }

    @GetMapping("/get-by-nickname")
    public Optional<SnmpCredentialDTO> getByNickname(SnmpCredentialGetRequest request) {
        throw new UnsupportedOperationException();
    }

    @PostMapping("/update")
    public SnmpCredentialUpdateResponse update(@RequestBody SnmpCredentialPatch snmpCredentialPatch) {
        throw new UnsupportedOperationException();
    }

    @PostMapping("/delete")
    public SnmpCredentialDeleteResponse deleteByNickname(@RequestBody SnmpCredentialDeleteRequest request) {
        throw new UnsupportedOperationException();
    }

    // requests
    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class SnmpCredentialCreateRequest {
        private final String nickname;
        private final String username;
        private final String password;
        private final String organization;
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class SnmpCredentialGetRequest {
        private final String nickname;
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class SnmpCredentialPatch {
        private final String nickname;
        private String username;
        private String password;
        private String organization;
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class SnmpCredentialDeleteRequest {
        private final String nickname;
    }


    // responses
    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class SnmpCredentialCreateResponse {
        private final Boolean success;
        private final Optional<SnmpCredentialDTO> snmpCredential;
        private final Optional<String> error;
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class SnmpCredentialUpdateResponse {
        private final Boolean success;
        private final Optional<SnmpCredentialDTO> updatedSnmpCredential;
        private final Optional<String> error;
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class SnmpCredentialDeleteResponse {
        private final Boolean success;
        private final Optional<String> error;
    }
}
