package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuOrganization;

import java.util.List;

@Repository
public interface RsuOrganizationRepository extends JpaRepository<RsuOrganization, Integer> {

    /**
     * Add RSU to organization relationship
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO rsu_organization(rsu_id, organization_id) " +
            "VALUES (" +
            "(SELECT rsu_id FROM rsus WHERE ipv4_address = :rsuIp), " +
            "(SELECT organization_id FROM organizations WHERE name = :organizationName)" +
            ")", nativeQuery = true)
    void addRsuToOrganization(
            @Param("rsuIp") String rsuIp,
            @Param("organizationName") String organizationName);

    /**
     * Remove RSU from organizations
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM rsu_organization " +
            "WHERE rsu_id = (SELECT rsu_id FROM rsus WHERE ipv4_address = :rsuIp) " +
            "AND organization_id IN (SELECT organization_id FROM organizations WHERE name IN :organizationNames)", nativeQuery = true)
    void removeRsuFromOrganizations(
            @Param("rsuIp") String rsuIp,
            @Param("organizationNames") List<String> organizationNames);

    /**
     * Delete all organization relationships for an RSU
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM rsu_organization " +
            "WHERE rsu_id = (SELECT rsu_id FROM rsus WHERE ipv4_address = :rsuIp)", nativeQuery = true)
    void deleteAllByRsuIpv4Address(@Param("rsuIp") String rsuIp);
}
