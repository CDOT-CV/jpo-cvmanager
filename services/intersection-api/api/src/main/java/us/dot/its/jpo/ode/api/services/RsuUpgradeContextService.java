package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import lombok.RequiredArgsConstructor;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.repositories.RsuOrganizationRepository;

@Service
@RequiredArgsConstructor
public class RsuUpgradeContextService {

    private final RsuOrganizationRepository rsuOrganizationRepository;

    public boolean hasCompleteRsuData(String rsuIp, String organization) {
        return findRsuForOrganization(rsuIp, organization) != null;
    }

    public Rsu findRsuForOrganization(String rsuIp, String organization) {
        InetAddress inetAddress = parseIpv4Address(rsuIp);

        return rsuOrganizationRepository
                .findByRsuIpv4AddressAndOrganization_Name(inetAddress, organization)
                .map(ro -> ro.getRsu())
                .orElse(null);
    }

    private InetAddress parseIpv4Address(String rsuIp) {
        try {
            return InetAddress.getByName(rsuIp);
        } catch (UnknownHostException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid RSU IP address: " + rsuIp, e);
        }
    }
}
