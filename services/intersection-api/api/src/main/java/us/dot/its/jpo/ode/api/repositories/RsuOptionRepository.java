package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.RsuOption;

import java.util.Optional;

@Repository
public interface RsuOptionRepository extends JpaRepository<RsuOption, Integer> {
    Optional<RsuOption> findByRsuId(Integer rsuId);
}

