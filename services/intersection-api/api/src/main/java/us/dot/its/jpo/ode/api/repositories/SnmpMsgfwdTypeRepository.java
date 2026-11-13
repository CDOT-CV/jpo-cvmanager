package us.dot.its.jpo.ode.api.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdType;

public interface SnmpMsgfwdTypeRepository extends JpaRepository<SnmpMsgfwdType, Integer> {
    Optional<SnmpMsgfwdType> findByName(String name);
}
