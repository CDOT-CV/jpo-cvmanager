package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Repository for SNMP message forward configuration
 * Used for cleanup operations when deleting RSUs
 */
@Repository
public interface SnmpMsgfwdConfigRepository extends JpaRepository<Object, UUID> {

    /**
     * Delete all SNMP message forward config records for an RSU
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM snmp_msgfwd_config " +
            "WHERE rsu_id = (SELECT rsu_id FROM rsus WHERE ipv4_address = :rsuIp)", nativeQuery = true)
    void deleteAllByRsuIpv4Address(@Param("rsuIp") String rsuIp);
}
