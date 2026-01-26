package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import us.dot.its.jpo.ode.api.models.postgres.tables.Ping;

/**
 * Repository for RSU ping data
 * Used for cleanup operations when deleting RSUs
 */
@Repository
public interface PingRepository extends JpaRepository<Ping, Integer> {

    /**
     * Delete all ping records for an RSU
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM ping " +
            "WHERE rsu_id = (SELECT rsu_id FROM rsus WHERE ipv4_address = :rsuIp)", nativeQuery = true)
    void deleteAllByRsuIpv4Address(@Param("rsuIp") String rsuIp);
}
