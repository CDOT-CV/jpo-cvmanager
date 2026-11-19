package us.dot.its.jpo.ode.api.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import us.dot.its.jpo.ode.api.controllers.credentials.RsuCredentialController;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuCredentialRepository;
import us.dot.its.jpo.ode.api.models.credentials.CredentialSummaryDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RsuCredentialManagementService {
    private final RsuCredentialRepository rsuCredentialRepository;
    private final OrganizationRepository organizationRepository;

    public RsuCredential create(RsuCredentialController.RsuCredentialCreateRequest rsuCredentialCreateRequest)
            throws RsuCredentialAlreadyExistsException, EntityNotFoundException {
        if (rsuCredentialRepository.existsByNickname(rsuCredentialCreateRequest.getNickname())) {
            throw new RsuCredentialAlreadyExistsException("RSU Credential already exists");
        }
        RsuCredential rsuCredential = new RsuCredential();
        rsuCredential.setNickname(rsuCredentialCreateRequest.getNickname());
        rsuCredential.setUsername(rsuCredentialCreateRequest.getUsername());
        rsuCredential.setPassword(rsuCredentialCreateRequest.getPassword());

        Organization organization = organizationRepository.findByName(rsuCredentialCreateRequest.getOrganization())
                .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
        rsuCredential.setOwnerOrganization(organization);

        return rsuCredentialRepository.save(rsuCredential);
    }

    public RsuCredential getByNickname(String nickname) throws EntityNotFoundException {
        return rsuCredentialRepository.findByNickname(nickname)
                .orElseThrow(() -> new EntityNotFoundException("RSU Credential not found"));
    }

    /**
     * Lists the SSH credentials visible to an organization: those it owns and
     * those used by RSUs associated with it. Passwords are not included.
     */
    public List<CredentialSummaryDTO> listForOrganization(String organizationName) throws EntityNotFoundException {
        Organization organization = organizationRepository.findByName(organizationName)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found"));

        Map<String, List<String>> rsuIpsByNickname = new HashMap<>();
        for (RsuCredentialRepository.CredentialRsuUsage usage : rsuCredentialRepository
                .findRsuUsageInOrganization(organization)) {
            rsuIpsByNickname.computeIfAbsent(usage.getNickname(), nickname -> new ArrayList<>())
                    .add(usage.getIpv4Address().getHostAddress());
        }

        return rsuCredentialRepository.findOwnedOrUsedByOrganization(organization).stream()
                .map(credential -> new CredentialSummaryDTO(
                        credential.getNickname(),
                        credential.getUsername(),
                        credential.getOwnerOrganization().getName(),
                        rsuIpsByNickname.getOrDefault(credential.getNickname(), List.of())))
                .toList();
    }

    public RsuCredential update(RsuCredentialController.RsuCredentialPatch rsuCredentialPatch)
            throws EntityNotFoundException {
        RsuCredential rsuCredential = rsuCredentialRepository.findByNickname(rsuCredentialPatch.getNickname())
                .orElseThrow(() -> new EntityNotFoundException("RSU Credential not found"));

        if (rsuCredentialPatch.getUsername() != null) {
            rsuCredential.setUsername(rsuCredentialPatch.getUsername());
        }
        if (rsuCredentialPatch.getPassword() != null) {
            rsuCredential.setPassword(rsuCredentialPatch.getPassword());
        }
        if (rsuCredentialPatch.getOrganization() != null) {
            Organization newOrganization = organizationRepository.findByName(rsuCredentialPatch.getOrganization())
                    .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
            rsuCredential.setOwnerOrganization(newOrganization);
        }
        return rsuCredentialRepository.save(rsuCredential);
    }

    public void deleteByNickname(String nickname) throws EntityNotFoundException {
        RsuCredential rsuCredential = rsuCredentialRepository.findByNickname(nickname)
                .orElseThrow(() -> new EntityNotFoundException("RSU Credential not found"));

        rsuCredentialRepository.delete(rsuCredential);
    }

    public static class RsuCredentialAlreadyExistsException extends Exception {
        public RsuCredentialAlreadyExistsException(String message) {
            super(message);
        }
    }
}
