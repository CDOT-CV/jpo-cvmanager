package us.dot.its.jpo.ode.api.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import us.dot.its.jpo.ode.api.controllers.credentials.RsuCredentialController;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.repositories.RsuCredentialRepository;

@Service
@RequiredArgsConstructor
public class RsuCredentialManagementService {
    private final RsuCredentialRepository rsuCredentialRepository;

    public RsuCredential createRsuCredential(RsuCredentialController.RsuCredentialCreateRequest rsuCredentialCreateRequest) {
        throw new UnsupportedOperationException();
    }

    public RsuCredential getByNickname(String nickname) {
        throw new UnsupportedOperationException();
    }

    public RsuCredential update(RsuCredentialController.RsuCredentialPatch rsuCredentialPatch) {
        throw new UnsupportedOperationException();
    }

    public boolean deleteByNickname(String nickname) {
        throw new UnsupportedOperationException();
    }
}
