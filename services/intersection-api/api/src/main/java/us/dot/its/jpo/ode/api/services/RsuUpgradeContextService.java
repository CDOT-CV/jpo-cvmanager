package us.dot.its.jpo.ode.api.services;

import java.net.Inet4Address;
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
        Inet4Address Inet4Address = RsuManagementService.toIpv4Address(rsuIp);
        return rsuRepository.findByIpv4Address(Inet4Address);
    }
}
