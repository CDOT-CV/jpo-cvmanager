package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

@Service
@RequiredArgsConstructor
public class RsuUpgradeContextService {

    private final RsuRepository rsuRepository;

    public boolean hasCompleteRsuData(String rsuIp) {
        return findRsuByIp(rsuIp) != null;
    }

    public Rsu findRsuByIp(String rsuIp) {
        InetAddress inetAddress = RsuManagementService.toIpv4Address(rsuIp);
        return rsuRepository.findByIpv4Address(inetAddress);
    }
}
