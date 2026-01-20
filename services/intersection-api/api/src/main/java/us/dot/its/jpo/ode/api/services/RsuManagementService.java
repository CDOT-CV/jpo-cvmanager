package us.dot.its.jpo.ode.api.services;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import us.dot.its.jpo.ode.api.models.devices.management.ModifyRsuAllowedSelections;
import us.dot.its.jpo.ode.api.repositories.RsuCredentialsRepository;
import us.dot.its.jpo.ode.api.repositories.RsuModelsRepository;
import us.dot.its.jpo.ode.api.repositories.RsusRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpCredentialsRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpProtocolsRepository;

@Service
@RequiredArgsConstructor
public class RsuManagementService {
    private RsusRepository rsusRepository;
    private RsuModelsRepository rsuModelsRepository;
    private RsuCredentialsRepository rsuCredentialsRepository;
    private SnmpCredentialsRepository snmpCredentialsRepository;
    private SnmpProtocolsRepository snmpProtocolsRepository;
    private PostgresService postgresService;

    public ModifyRsuAllowedSelections getAllowedSelections(String username) {
        ModifyRsuAllowedSelections allowed = new ModifyRsuAllowedSelections();

        allowed.setPrimaryRoutes(rsusRepository.findDistinctPrimaryRoutes());
        allowed.setRsuModels(rsuModelsRepository.findAllModelsWithManufacturers());
        allowed.setSshCredentialGroups(rsuCredentialsRepository.findAllNicknames());
        allowed.setSnmpCredentialGroups(snmpCredentialsRepository.findAllNicknames());
        allowed.setSnmpVersionGroups(snmpProtocolsRepository.findAllNicknames());
        allowed.setOrganizations(postgresService.findUserOrgRoles(username).stream()
                .map(role -> role.getOrganization_name()).toList());

        return allowed;
    }
}
