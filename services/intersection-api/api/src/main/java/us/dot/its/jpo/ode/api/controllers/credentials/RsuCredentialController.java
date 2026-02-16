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
import us.dot.its.jpo.ode.api.mappers.RsuCredentialMapper;
import us.dot.its.jpo.ode.api.models.credentials.RsuCredentialDTO;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.services.RsuCredentialManagementService;

import java.util.Optional;

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

    // TODO: update endpoints to use `@PreAuthorize` annotation

    @PostMapping("/create")
    public RsuCredentialCreateResponse createRsuCredential(RsuCredentialCreateRequest rsuCredentialCreateRequest) {
        RsuCredential rsuCredential = null;
        try {
            rsuCredential = rsuCredentialManagementService.create(rsuCredentialCreateRequest);
        } catch (RsuCredentialManagementService.RsuCredentialAlreadyExistsException e) {
            return new RsuCredentialCreateResponse(false, Optional.empty(), Optional.of("RSU Credential already exists"));
        } catch (RsuCredentialManagementService.OrganizationNotFoundException e) {
            return new RsuCredentialCreateResponse(false, Optional.empty(), Optional.of("Organization not found"));
        }
        return new RsuCredentialCreateResponse(true, Optional.of(rsuCredentialMapper.toDto(rsuCredential)), Optional.empty());
    }

    @GetMapping("/get-by-nickname")
    public Optional<RsuCredentialDTO> getByNickname(RsuCredentialGetRequest rsuCredentialGetRequest) {
        RsuCredential rsuCredential = null;
        try {
            rsuCredential = rsuCredentialManagementService.getByNickname(rsuCredentialGetRequest.getNickname());
        } catch (RsuCredentialManagementService.RsuCredentialNotFoundException e) {
            return Optional.empty();
        }
        return Optional.of(rsuCredentialMapper.toDto(rsuCredential));
    }

    @PostMapping("/update")
    public RsuCredentialUpdateResponse update(@RequestBody RsuCredentialPatch rsuCredentialPatch) {
        RsuCredential updatedRsuCredential = null;
        try {
            updatedRsuCredential = rsuCredentialManagementService.update(rsuCredentialPatch);
        } catch (RsuCredentialManagementService.RsuCredentialNotFoundException e) {
            return new RsuCredentialUpdateResponse(false, Optional.empty(), Optional.of("RSU Credential not found"));
        }
        return new RsuCredentialUpdateResponse(true, Optional.of(rsuCredentialMapper.toDto(updatedRsuCredential)), Optional.empty());
    }

    @PostMapping("/delete")
    public RsuCredentialDeleteResponse deleteByNickname(@RequestBody RsuCredentialDeleteRequest rsuCredentialDeleteRequest) {
        try {
            rsuCredentialManagementService.deleteByNickname(rsuCredentialDeleteRequest.getNickname());
        } catch (RsuCredentialManagementService.RsuCredentialNotFoundException e) {
            return new RsuCredentialDeleteResponse(false, Optional.of("RSU Credential not found"));
        }
        return new RsuCredentialDeleteResponse(true, Optional.empty());
    }

    // requests
    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class RsuCredentialCreateRequest {
        private final String nickname;
        private final String username;
        private final String password;
        private final String organization;
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class RsuCredentialGetRequest {
        private final String nickname;
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class RsuCredentialPatch {
        private final String nickname;
        private String username;
        private String password;
        private String organization;
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class RsuCredentialDeleteRequest {
        private final String nickname;
    }

    // responses
    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class RsuCredentialCreateResponse {
        private final Boolean success;
        private final Optional<RsuCredentialDTO> rsuCredential;
        private final Optional<String> error;
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class RsuCredentialUpdateResponse {
        private final Boolean success;
        private final Optional<RsuCredentialDTO> updatedRsuCredential;
        private final Optional<String> error;
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class RsuCredentialDeleteResponse {
        private final Boolean success;
        private final Optional<String> error;
    }
}
