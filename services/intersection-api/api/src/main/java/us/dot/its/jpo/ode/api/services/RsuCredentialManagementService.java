package us.dot.its.jpo.ode.api.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.repositories.RsuCredentialRepository;

@Service
@RequiredArgsConstructor
public class RsuCredentialManagementService {
    private final RsuCredentialRepository rsuCredentialRepository;

    public RsuCredential createRsuCredential(String nickname, String username, String password, String organization) {
        throw new UnsupportedOperationException();
    }

    // TODO: implement CRUD operations
}
