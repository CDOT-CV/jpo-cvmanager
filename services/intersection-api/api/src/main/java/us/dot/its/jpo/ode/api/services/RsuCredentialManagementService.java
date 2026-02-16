package us.dot.its.jpo.ode.api.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import us.dot.its.jpo.ode.api.controllers.credentials.RsuCredentialController;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuCredentialRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RsuCredentialManagementService {
    private final RsuCredentialRepository rsuCredentialRepository;
    private final OrganizationRepository organizationRepository;

    public RsuCredential create(RsuCredentialController.RsuCredentialCreateRequest rsuCredentialCreateRequest) throws RsuCredentialAlreadyExistsException, OrganizationNotFoundException {
        if (rsuCredentialRepository.existsByNickname(rsuCredentialCreateRequest.getNickname())) {
            throw new RsuCredentialAlreadyExistsException("RSU Credential already exists");
        }
        RsuCredential rsuCredential = new RsuCredential();
        rsuCredential.setNickname(rsuCredentialCreateRequest.getNickname());
        rsuCredential.setUsername(rsuCredentialCreateRequest.getUsername());
        rsuCredential.setPassword(rsuCredentialCreateRequest.getPassword());

        Optional<Organization> organization = organizationRepository.findByName(rsuCredentialCreateRequest.getOrganization());
        if (organization.isEmpty()) {
            throw new OrganizationNotFoundException("Organization not found");
        }
        int organizationId = organization.get().getId();
        rsuCredential.setOwnerOrganizationId(organizationId);

        return rsuCredentialRepository.save(rsuCredential);
    }

    public RsuCredential getByNickname(String nickname) throws RsuCredentialNotFoundException {
        return rsuCredentialRepository.findByNickname(nickname).orElseThrow(() -> new RsuCredentialNotFoundException("RSU Credential not found"));
    }

    public RsuCredential update(RsuCredentialController.RsuCredentialPatch rsuCredentialPatch) throws RsuCredentialNotFoundException {
        throw new UnsupportedOperationException();
    }

    public boolean deleteByNickname(String nickname) throws RsuCredentialNotFoundException {
        throw new UnsupportedOperationException();
    }

    public static class RsuCredentialNotFoundException extends Exception {
        public RsuCredentialNotFoundException(String message) {
            super(message);
        }
    }

    public static class RsuCredentialAlreadyExistsException extends Exception {
        public RsuCredentialAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class OrganizationNotFoundException extends Exception {
        public OrganizationNotFoundException(String message) {
            super(message);
        }
    }
}
