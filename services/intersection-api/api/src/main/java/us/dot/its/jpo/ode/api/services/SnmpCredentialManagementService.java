package us.dot.its.jpo.ode.api.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import us.dot.its.jpo.ode.api.controllers.credentials.SnmpCredentialController;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;
import us.dot.its.jpo.ode.api.repositories.SnmpCredentialRepository;

@Service
@RequiredArgsConstructor
public class SnmpCredentialManagementService {
    private final SnmpCredentialRepository snmpCredentialRepository;

    public SnmpCredential create(SnmpCredentialController.SnmpCredentialCreateRequest request) throws SnmpCredentialAlreadyExistsException, OrganizationNotFoundException {
        throw new UnsupportedOperationException();
    }

    public SnmpCredential getByNickname(String nickname) throws SnmpCredentialNotFoundException {
        throw new UnsupportedOperationException();
    }

    public SnmpCredential update(SnmpCredentialController.SnmpCredentialPatch patch) throws SnmpCredentialNotFoundException, OrganizationNotFoundException {
        throw new UnsupportedOperationException();
    }

    public boolean deleteByNickname(String nickname) throws SnmpCredentialNotFoundException {
        throw new UnsupportedOperationException();
    }

    // TODO: implement CRUD operations

    public static class SnmpCredentialNotFoundException extends Exception {
        public SnmpCredentialNotFoundException(String message) {
            super(message);
        }
    }

    public static class SnmpCredentialAlreadyExistsException extends Exception {
        public SnmpCredentialAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class OrganizationNotFoundException extends Exception {
        public OrganizationNotFoundException(String message) {
            super(message);
        }
    }
}
