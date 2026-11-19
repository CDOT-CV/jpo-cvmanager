package us.dot.its.jpo.ode.api.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import us.dot.its.jpo.ode.api.controllers.credentials.SnmpCredentialController;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpCredentialRepository;
import us.dot.its.jpo.ode.api.models.credentials.CredentialSummaryDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SnmpCredentialManagementService {
    private final SnmpCredentialRepository snmpCredentialRepository;
    private final OrganizationRepository organizationRepository;

    public SnmpCredential create(SnmpCredentialController.SnmpCredentialCreateRequest request) throws SnmpCredentialAlreadyExistsException, EntityNotFoundException {
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
        snmpCredential.setOwnerOrganization(organization.get());

        return snmpCredentialRepository.save(snmpCredential);
    }

    public SnmpCredential getByNickname(String nickname) throws EntityNotFoundException {
        return snmpCredentialRepository.findByNickname(nickname).orElseThrow(() -> new EntityNotFoundException("No credential found with nickname " + nickname));
    }

    /**
     * Lists the SNMP credentials visible to an organization: those it owns and
     * those used by RSUs associated with it. Passwords are not included.
     */
    public List<CredentialSummaryDTO> listForOrganization(String organizationName) throws EntityNotFoundException {
        Organization organization = organizationRepository.findByName(organizationName)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found"));

        Map<String, List<String>> rsuIpsByNickname = new HashMap<>();
        for (SnmpCredentialRepository.CredentialRsuUsage usage : snmpCredentialRepository
                .findRsuUsageInOrganization(organization)) {
            rsuIpsByNickname.computeIfAbsent(usage.getNickname(), nickname -> new ArrayList<>())
                    .add(usage.getIpv4Address().getHostAddress());
        }

        return snmpCredentialRepository.findOwnedOrUsedByOrganization(organization).stream()
                .map(credential -> new CredentialSummaryDTO(
                        credential.getNickname(),
                        credential.getUsername(),
                        credential.getOwnerOrganization().getName(),
                        rsuIpsByNickname.getOrDefault(credential.getNickname(), List.of())))
                .toList();
    }

    public SnmpCredential update(SnmpCredentialController.SnmpCredentialPatch patch) throws EntityNotFoundException {
        SnmpCredential credential = snmpCredentialRepository.findByNickname(patch.getNickname()).orElseThrow(() -> new EntityNotFoundException("No credential found with nickname " + patch.getNickname()));

        if (patch.getUsername() != null) {
            credential.setUsername(patch.getUsername());
        }
        if (patch.getPassword() != null) {
            credential.setPassword(patch.getPassword());
        }
        if (patch.getOrganization() != null) {
            Optional<Organization> newOrganization = organizationRepository.findByName(patch.getOrganization());
            if (newOrganization.isEmpty()) {
                throw new EntityNotFoundException("Organization " + patch.getOrganization() + " not found.");
            }
            credential.setOwnerOrganization(newOrganization.get());
        }
        return snmpCredentialRepository.save(credential);
    }

    public void deleteByNickname(String nickname) throws EntityNotFoundException {
        Optional<SnmpCredential> credentialOptional = snmpCredentialRepository.findByNickname(nickname);
        if (credentialOptional.isEmpty()) {
            throw new EntityNotFoundException("No credential found with nickname " + nickname);
        }
        SnmpCredential credential = credentialOptional.get();

        snmpCredentialRepository.delete(credential);
    }

    public static class SnmpCredentialAlreadyExistsException extends Exception {
        public SnmpCredentialAlreadyExistsException(String message) {
            super(message);
        }
    }
}
