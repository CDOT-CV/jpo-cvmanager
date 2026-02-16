package us.dot.its.jpo.ode.api.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import us.dot.its.jpo.ode.api.repositories.SnmpCredentialRepository;

@Service
@RequiredArgsConstructor
public class SnmpCredentialManagementService {
    private final SnmpCredentialRepository snmpCredentialRepository;

    // TODO: implement CRUD operations
}
