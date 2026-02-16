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

    @PostMapping("/create")
    public RsuCredentialDTO createRsuCredential(RsuCredentialCreateRequest rsuCredentialCreateRequest) {
        RsuCredential rsuCredential = rsuCredentialManagementService.createRsuCredential(rsuCredentialCreateRequest);
        return rsuCredentialMapper.toDto(rsuCredential);
    }

    @GetMapping("/get-by-nickname")
    public RsuCredentialDTO getByNickname(RsuCredentialGetRequest rsuCredentialGetRequest) {
        RsuCredential rsuCredential = rsuCredentialManagementService.getByNickname(rsuCredentialGetRequest.getNickname());
        return rsuCredentialMapper.toDto(rsuCredential);
    }

    @PostMapping("/update")
    public RsuCredentialUpdateResponse update(@RequestBody RsuCredentialPatch rsuCredentialPatch) {
        RsuCredential updatedRsuCredential = rsuCredentialManagementService.update(rsuCredentialPatch);
        if (updatedRsuCredential == null) {
            return new RsuCredentialUpdateResponse(false, Optional.empty(), Optional.of("RSU Credential not found"));
        }
        return new RsuCredentialUpdateResponse(true, Optional.of(rsuCredentialMapper.toDto(updatedRsuCredential)), Optional.empty());
    }

    @PostMapping("/delete")
    public RsuCredentialDeleteResponse deleteByNickname(@RequestBody RsuCredentialDeleteRequest rsuCredentialDeleteRequest) {
        boolean success = rsuCredentialManagementService.deleteByNickname(rsuCredentialDeleteRequest.getNickname());
        if (!success) {
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
