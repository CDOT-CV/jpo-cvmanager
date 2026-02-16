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

    public RsuCredential createRsuCredential(RsuCredentialController.RsuCredentialCreateRequest rsuCredentialCreateRequest) throws RsuCredentialAlreadyExistsException {
        throw new UnsupportedOperationException();
    }

    public RsuCredential getByNickname(String nickname) throws RsuCredentialNotFoundException {
        throw new UnsupportedOperationException();
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
}
