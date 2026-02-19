package us.dot.its.jpo.ode.api.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import us.dot.its.jpo.ode.api.controllers.credentials.RsuCredentialController;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuCredentialRepository;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RsuCredentialManagementService {
    private final RsuCredentialRepository rsuCredentialRepository;
    private final OrganizationRepository organizationRepository;

    public RsuCredential create(String organizationName, RsuCredentialController.RsuCredentialCreateRequest rsuCredentialCreateRequest) throws RsuCredentialAlreadyExistsException, EntityNotFoundException {
        if (!rsuCredentialCreateRequest.getOrganization().equals(organizationName)) {
            throw new AccessDeniedException("Organization in request body does not match Organization header");
        }

        if (rsuCredentialRepository.existsByNickname(rsuCredentialCreateRequest.getNickname())) {
            throw new RsuCredentialAlreadyExistsException("RSU Credential already exists");
        }
        RsuCredential rsuCredential = new RsuCredential();
        rsuCredential.setNickname(rsuCredentialCreateRequest.getNickname());
        rsuCredential.setUsername(rsuCredentialCreateRequest.getUsername());
        rsuCredential.setPassword(rsuCredentialCreateRequest.getPassword());

        Optional<Organization> organization = organizationRepository.findByName(rsuCredentialCreateRequest.getOrganization());
        if (organization.isEmpty()) {
            throw new EntityNotFoundException("Organization not found");
        }
        int organizationId = organization.get().getId();
        rsuCredential.setOwnerOrganizationId(organizationId);

        return rsuCredentialRepository.save(rsuCredential);
    }

    public RsuCredential getByNickname(String organizationName, String nickname) throws EntityNotFoundException {
        RsuCredential rsuCredential = rsuCredentialRepository.findByNickname(nickname).orElseThrow(() -> new EntityNotFoundException("RSU Credential not found"));
        
        Optional<Organization> organization = organizationRepository.findByName(organizationName);
        if (organization.isEmpty()) {
            throw new EntityNotFoundException("Organization not found");
        }

        if (!Objects.equals(rsuCredential.getOwnerOrganizationId(), organization.get().getId())) {
            throw new AccessDeniedException("User does not have permission to access this credential");
        }

        return rsuCredential;
    }

    public RsuCredential update(String organizationName, RsuCredentialController.RsuCredentialPatch rsuCredentialPatch) throws EntityNotFoundException {
        RsuCredential rsuCredential = rsuCredentialRepository.findByNickname(rsuCredentialPatch.getNickname()).orElseThrow(() -> new EntityNotFoundException("RSU Credential not found"));
        
        Optional<Organization> organization = organizationRepository.findByName(organizationName);
        if (organization.isEmpty()) {
            throw new EntityNotFoundException("Organization not found");
        }

        if (!Objects.equals(rsuCredential.getOwnerOrganizationId(), organization.get().getId())) {
            throw new AccessDeniedException("User does not have permission to access this credential");
        }

        if (rsuCredentialPatch.getUsername() != null) {
            rsuCredential.setUsername(rsuCredentialPatch.getUsername());
        }
        if (rsuCredentialPatch.getPassword() != null) {
            rsuCredential.setPassword(rsuCredentialPatch.getPassword());
        }
        if (rsuCredentialPatch.getOrganization() != null) {
            if (!rsuCredentialPatch.getOrganization().equals(organizationName)) {
                throw new AccessDeniedException("Organization in request body does not match Organization header");
            }

            Optional<Organization> newOrganization = organizationRepository.findByName(rsuCredentialPatch.getOrganization());
            if (newOrganization.isEmpty()) {
                throw new EntityNotFoundException("Organization not found");
            }
            rsuCredential.setOwnerOrganizationId(newOrganization.get().getId());
        }
        return rsuCredentialRepository.save(rsuCredential);
    }

    public void deleteByNickname(String organizationName, String nickname) throws EntityNotFoundException {
        Optional<RsuCredential> rsuCredentialOptional = rsuCredentialRepository.findByNickname(nickname);
        if (rsuCredentialOptional.isEmpty()) {
            throw new EntityNotFoundException("RSU Credential not found");
        }
        RsuCredential rsuCredential = rsuCredentialOptional.get();

        Optional<Organization> organization = organizationRepository.findByName(organizationName);
        if (organization.isEmpty()) {
            throw new EntityNotFoundException("Organization not found");
        }

        if (!Objects.equals(rsuCredential.getOwnerOrganizationId(), organization.get().getId())) {
            throw new AccessDeniedException("User does not have permission to access this credential");
        }

        rsuCredentialRepository.delete(rsuCredential);
    }

    public static class RsuCredentialAlreadyExistsException extends Exception {
        public RsuCredentialAlreadyExistsException(String message) {
            super(message);
        }
    }
}
