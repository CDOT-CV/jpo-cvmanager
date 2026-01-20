package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredentials;

import java.util.Optional;

@Repository
public interface RsuCredentialsRepository extends JpaRepository<RsuCredentials, Integer> {

    /**
     * Find RSU credentials by nickname
     */
    Optional<RsuCredentials> findByNickname(String nickname);
}
