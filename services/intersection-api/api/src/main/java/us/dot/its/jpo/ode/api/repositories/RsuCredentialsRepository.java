package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredentials;

import java.util.List;
import java.util.Optional;

@Repository
public interface RsuCredentialsRepository extends JpaRepository<RsuCredentials, Integer> {

    /**
     * Find RSU credentials by nickname
     */
    Optional<RsuCredentials> findByNickname(String nickname);

    /**
     * Get all RSU credential nicknames
     * Matches Python: get_allowed_selections() - ssh_credential_nicknames_query
     */
    @Query("SELECT rc.nickname FROM RsuCredentials rc ORDER BY rc.nickname ASC")
    List<String> findAllNicknames();
}
