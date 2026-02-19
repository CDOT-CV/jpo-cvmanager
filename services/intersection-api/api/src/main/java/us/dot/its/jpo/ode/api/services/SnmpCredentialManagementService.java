package us.dot.its.jpo.ode.api.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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

    public SnmpCredential create(String organizationName, SnmpCredentialController.SnmpCredentialCreateRequest request) throws SnmpCredentialAlreadyExistsException, EntityNotFoundException {
        if (!request.getOrganization().equals(organizationName)) {
            throw new AccessDeniedException("Organization in request body does not match Organization header");
        }

        if (snmpCredentialRepository.existsByNickname(request.getNickname())) {
            throw new SnmpCredentialAlreadyExistsException("A credential with nickname " + request.getNickname() + " already exists.");
        }
        SnmpCredential snmpCredential = new SnmpCredential();
        snmpCredential.setNickname(request.getNickname());
        snmpCredential.setUsername(request.getUsername());
        snmpCredential.setPassword(request.getPassword());

        Optional<Organization> organization = organizationRepository.findByName(request.getOrganization());
        if (organization.isEmpty()) {
            throw new EntityNotFoundException("Organization " + request.getOrganization() + " not found.");
        }
        int organizationId = organization.get().getId();
        snmpCredential.setOwnerOrganizationId(organizationId);

        return snmpCredentialRepository.save(snmpCredential);
    }

    public SnmpCredential getByNickname(String organizationName, String nickname) throws EntityNotFoundException {
        SnmpCredential credential = snmpCredentialRepository.findByNickname(nickname).orElseThrow(() -> new EntityNotFoundException("No credential found with nickname " + nickname));
        
        Optional<Organization> organization = organizationRepository.findByName(organizationName);
        if (organization.isEmpty()) {
            throw new EntityNotFoundException("Organization " + organizationName + " not found.");
        }

        if (credential.getOwnerOrganizationId() != organization.get().getId()) {
            throw new AccessDeniedException("User does not have permission to access this credential");
        }

        return credential;
    }

    public SnmpCredential update(String organizationName, SnmpCredentialController.SnmpCredentialPatch patch) throws EntityNotFoundException {
        SnmpCredential credential = snmpCredentialRepository.findByNickname(patch.getNickname()).orElseThrow(() -> new EntityNotFoundException("No credential found with nickname " + patch.getNickname()));
        
        Optional<Organization> organization = organizationRepository.findByName(organizationName);
        if (organization.isEmpty()) {
            throw new EntityNotFoundException("Organization " + organizationName + " not found.");
        }

        if (credential.getOwnerOrganizationId() != organization.get().getId()) {
            throw new AccessDeniedException("User does not have permission to access this credential");
        }

        if (patch.getUsername() != null) {
            credential.setUsername(patch.getUsername());
        }
        if (patch.getPassword() != null) {
            credential.setPassword(patch.getPassword());
        }
        if (patch.getOrganization() != null) {
            if (!patch.getOrganization().equals(organizationName)) {
                throw new AccessDeniedException("Organization in request body does not match Organization header");
            }

            Optional<Organization> newOrganization = organizationRepository.findByName(patch.getOrganization());
            if (newOrganization.isEmpty()) {
                throw new EntityNotFoundException("Organization " + patch.getOrganization() + " not found.");
            }
            credential.setOwnerOrganizationId(newOrganization.get().getId());
        }
        return snmpCredentialRepository.save(credential);
    }

    public void deleteByNickname(String organizationName, String nickname) throws EntityNotFoundException {
        Optional<SnmpCredential> credentialOptional = snmpCredentialRepository.findByNickname(nickname);
        if (credentialOptional.isEmpty()) {
            throw new EntityNotFoundException("No credential found with nickname " + nickname);
        }
        SnmpCredential credential = credentialOptional.get();

        Optional<Organization> organization = organizationRepository.findByName(organizationName);
        if (organization.isEmpty()) {
            throw new EntityNotFoundException("Organization " + organizationName + " not found.");
        }

        if (credential.getOwnerOrganizationId() != organization.get().getId()) {
            throw new AccessDeniedException("User does not have permission to access this credential");
        }

        snmpCredentialRepository.delete(credential);
    }

    public static class SnmpCredentialAlreadyExistsException extends Exception {
        public SnmpCredentialAlreadyExistsException(String message) {
            super(message);
        }
    }
}
