package us.dot.its.jpo.ode.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import us.dot.its.jpo.ode.api.models.postgres.tables.ScmsHealth;
import us.dot.its.jpo.ode.api.repositories.ScmsHealthRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScmsHealthService {
    private final ScmsHealthRepository scmsHealthRepository;

    public List<ScmsHealth> getScmsStatuses(String organization) {
        // TODO: implement
        throw new UnsupportedOperationException();
    }
}
