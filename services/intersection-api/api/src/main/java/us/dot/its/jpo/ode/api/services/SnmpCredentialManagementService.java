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

        Optional<Organization> organization = organizationRepository.findByName(request.getOrganization()); // TODO: use orElseThrow here
        if (organization.isEmpty()) {
            throw new OrganizationNotFoundException("Organization " + request.getOrganization() + " not found.");
        }
        int organizationId = organization.get().getId();
        snmpCredential.setOwnerOrganizationId(organizationId);

        return snmpCredentialRepository.save(snmpCredential);
    }

    public SnmpCredential getByNickname(String nickname) throws SnmpCredentialNotFoundException {
        return snmpCredentialRepository.findByNickname(nickname).orElseThrow(() -> new SnmpCredentialNotFoundException("No credential found with nickname " + nickname));
    }

    public SnmpCredential update(SnmpCredentialController.SnmpCredentialPatch patch) throws SnmpCredentialNotFoundException, OrganizationNotFoundException {
        SnmpCredential credential = snmpCredentialRepository.findByNickname(patch.getNickname()).orElseThrow(() -> new SnmpCredentialNotFoundException("No credential found with nickname " + patch.getNickname()));
        if (patch.getUsername() != null) {
            credential.setUsername(patch.getUsername());
        }
        if (patch.getPassword() != null) {
            credential.setPassword(patch.getPassword());
        }
        if (patch.getOrganization() != null) {
            Optional<Organization> organization = organizationRepository.findByName(patch.getOrganization());
            if (organization.isEmpty()) {
                throw new OrganizationNotFoundException("Organization " + patch.getOrganization() + " not found.");
            }
            credential.setOwnerOrganizationId(organization.get().getId());
        }
        return snmpCredentialRepository.save(credential);
    }

    public boolean deleteByNickname(String nickname) throws SnmpCredentialNotFoundException {
        Optional<SnmpCredential> credential = snmpCredentialRepository.findByNickname(nickname);
        if (credential.isEmpty()) {
            return false;
        }
        snmpCredentialRepository.delete(credential.get());
        return true;
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
