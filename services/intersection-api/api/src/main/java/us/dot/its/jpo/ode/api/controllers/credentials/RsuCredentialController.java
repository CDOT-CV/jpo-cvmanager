package us.dot.its.jpo.ode.api.controllers.credentials;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
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
// TODO: rely on controller advice to handle exceptions
public class RsuCredentialController {
    private final RsuCredentialManagementService rsuCredentialManagementService;
    private final RsuCredentialMapper rsuCredentialMapper;

    // TODO: update endpoints to use `@PreAuthorize` annotation

    @PostMapping("/create")
    public RsuCredentialDTO createRsuCredential(RsuCredentialCreateRequest rsuCredentialCreateRequest) throws RsuCredentialManagementService.OrganizationNotFoundException, RsuCredentialManagementService.RsuCredentialAlreadyExistsException {
        return rsuCredentialMapper.toDto(rsuCredentialManagementService.create(rsuCredentialCreateRequest));
    }

    @GetMapping("/get-by-nickname")
    public RsuCredentialDTO getByNickname(RsuCredentialGetRequest rsuCredentialGetRequest) throws RsuCredentialManagementService.RsuCredentialNotFoundException {
        return rsuCredentialMapper.toDto(rsuCredentialManagementService.getByNickname(rsuCredentialGetRequest.getNickname()));
    }

    @PostMapping("/update")
    public RsuCredentialDTO update(@RequestBody RsuCredentialPatch rsuCredentialPatch) throws RsuCredentialManagementService.OrganizationNotFoundException, RsuCredentialManagementService.RsuCredentialNotFoundException {
        return rsuCredentialMapper.toDto(rsuCredentialManagementService.update(rsuCredentialPatch));
    }

    @PostMapping("/delete")
    public RsuCredentialDeleteResponse deleteByNickname(@RequestBody RsuCredentialDeleteRequest rsuCredentialDeleteRequest) {
        boolean result = rsuCredentialManagementService.deleteByNickname(rsuCredentialDeleteRequest.getNickname());
        if (!result) {
            return new RsuCredentialDeleteResponse(false, Optional.of("RSU Credential not found"));
        }
        return new RsuCredentialDeleteResponse(true, Optional.empty());
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

    // responses
    @Data
    public static class RsuCredentialDeleteResponse {
        private final Boolean success;
        private final Optional<String> error;
    }
}
