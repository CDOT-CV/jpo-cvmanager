package us.dot.its.jpo.ode.api.services;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.snmp.client.SnmpCredentials;
import us.dot.its.jpo.snmp.rsu.ntcip1218.Ntcip1218Device;

/**
 * Resolves database inventory into the credential-bearing device targets used
 * by message forwarding tasks. The complete batch must load successfully before
 * the caller submits any device commands. Authorization belongs to the
 * controller. This service checks existence and SNMP compatibility.
 */
@Service
@RequiredArgsConstructor
public class RsuMessageForwardingContextService {
    private final RsuRepository rsuRepository;

    /**
     * Materializes lazy credential relations inside a short database transaction,
     * so workers do not retain JPA entities or depend on the HTTP request's
     * persistence context. Inputs must already be validated IPv4 literals. A
     * missing or incompatible member rejects the whole batch.
     */
    @Transactional(readOnly = true)
    public List<DeviceContext> load(List<String> ips) {
        return ips.stream().map(ip -> {
            try {
                var rsu = rsuRepository.findByIpv4Address(RsuManagementService.toIpv4Address(ip));
                if (rsu == null)
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RSU not found: " + ip);
                if (rsu.getSnmpProtocol() == null || !"1218".equals(rsu.getSnmpProtocol().getProtocolCode())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "NTCIP 1218 is required for all RSUs. No forwarding commands sent.");
                }
                var credential = rsu.getSnmpCredential();
                if (credential == null)
                    throw new IllegalArgumentException();
                var device = new Ntcip1218Device(ip, new SnmpCredentials(credential.getUsername(),
                        credential.getPassword(), credential.getEncryptPassword()));
                return new DeviceContext(rsu.getId(), device);
            } catch (IllegalArgumentException | NullPointerException e) {
                // Never attach credential validation exceptions to an HTTP response.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid SNMP configuration for RSU " + ip + ". No forwarding commands sent.");
            }
        }).toList();
    }

    /**
     * Carries only the database identity and SNMP target needed by a worker;
     * logging omits credentials.
     */
    public record DeviceContext(Integer rsuId, Ntcip1218Device device) {
        @Override
        public String toString() {
            return "DeviceContext[rsuId=" + rsuId + "]";
        }
    }
}
