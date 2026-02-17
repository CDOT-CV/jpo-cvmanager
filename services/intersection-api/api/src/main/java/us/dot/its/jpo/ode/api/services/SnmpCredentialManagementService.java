package us.dot.its.jpo.ode.api.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import us.dot.its.jpo.ode.api.controllers.credentials.SnmpCredentialController;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpCredentialRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SnmpCredentialManagementService {
    private final SnmpCredentialRepository snmpCredentialRepository;
    private final OrganizationRepository organizationRepository;

    public SnmpCredential create(SnmpCredentialController.SnmpCredentialCreateRequest request) throws SnmpCredentialAlreadyExistsException, OrganizationNotFoundException {
        if (snmpCredentialRepository.existsByNickname(request.getNickname())) {
            throw new SnmpCredentialAlreadyExistsException("A credential with nickname " + request.getNickname() + " already exists.");
        }
        SnmpCredential snmpCredential = new SnmpCredential();
        snmpCredential.setNickname(request.getNickname());
        snmpCredential.setUsername(request.getUsername());
        snmpCredential.setPassword(request.getPassword());

        Optional<Organization> organization = organizationRepository.findByName(request.getOrganization());
        if (organization.isEmpty()) {
            throw new OrganizationNotFoundException("Organization " + request.getOrganization() + " not found.");
        }
        int organizationId = organization.get().getId();
        snmpCredential.setOwnerOrganizationId(organizationId);

        return snmpCredentialRepository.save(snmpCredential);
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
