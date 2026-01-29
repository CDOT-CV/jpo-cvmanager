package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import us.dot.its.jpo.ode.api.mappers.RsuMapper;
import us.dot.its.jpo.ode.api.models.devices.management.ModifyRsuAllowedSelections;
import us.dot.its.jpo.ode.api.models.postgres.dtos.RsuInfoDto;
import us.dot.its.jpo.ode.api.repositories.RsuCredentialRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpCredentialRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpProtocolRepository;
import us.dot.its.jpo.ode.api.repositories.UserRepository;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;

@Service
@RequiredArgsConstructor
public class RsuService {

    private final RsuRepository rsuRepository;
    private final RsuCredentialRepository rsuCredentialRepository;
    private final SnmpCredentialRepository snmpCredentialRepository;
    private final SnmpProtocolRepository snmpProtocolRepository;
    private final UserRepository userRepository;
    private final RsuMapper rsuMapper;

    public RsuInfoDto getRsuInfo(String ipv4Address) {
        try {
            Rsu rsu = rsuRepository.findByIpv4Address(InetAddress.getByName(ipv4Address));
            return rsu != null ? rsuMapper.toDto(rsu) : null;
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address: " + ipv4Address, e);
        }
    }

    public Page<RsuInfoDto> getAllRsuInfo(String orgName, Pageable pageable) {
        Page<Rsu> rsus = rsuRepository.findAllByOrganization(orgName, pageable);
        return rsus.map(rsuMapper::toDto);
    }

    public ModifyRsuAllowedSelections getAllowedSelections(String username) {
        ModifyRsuAllowedSelections allowed = new ModifyRsuAllowedSelections();

        allowed.setPrimaryRoutes(rsuRepository.findAllPrimaryRoutes());
        allowed.setRsuModels(rsuRepository.findAllRsuModels().stream()
                .map(v -> String.format("%s %s", v.getManufacturer(),
                        v.getModel()))
                .toList());
        allowed.setSshCredentialGroups(rsuCredentialRepository.findAllNicknames());
        allowed.setSnmpCredentialGroups(snmpCredentialRepository.findAllNicknames());
        allowed.setSnmpVersionGroups(snmpProtocolRepository.findAllNicknames());
        allowed.setOrganizations(userRepository.findUserOrgRoles(username).stream()
                .map(role -> role.getOrganizationName()).toList());

        return allowed;
    }
}