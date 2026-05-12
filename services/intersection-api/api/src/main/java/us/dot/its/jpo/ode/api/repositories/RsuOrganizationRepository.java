package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuOrganization;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;

@Repository
public interface RsuOrganizationRepository extends JpaRepository<RsuOrganization, Integer> {

    @Modifying
    @Transactional
    @Query("DELETE FROM RsuOrganization ro WHERE ro.rsu.ipv4Address = :ipv4Address")
    void removeRsuOrganizationByIpv4Address(@Param("ipv4Address") InetAddress ipv4Address);

    @Modifying
    @Transactional
    @Query("DELETE FROM RsuOrganization ro WHERE ro.rsu.ipv4Address IN :ipv4Addresses")
    void removeMultipleRsuOrganizationsByIpv4Address(@Param("ipv4Addresses") List<InetAddress> ipv4Addresses);

    @Query("SELECT ro FROM RsuOrganization ro WHERE ro.rsu.ipv4Address = :ipv4Address")
    List<RsuOrganization> findAllByIpv4Address(@Param("ipv4Address") InetAddress ipv4Address);

    Optional<RsuOrganization> findByOrganization_Id(Integer orgId);

    Optional<RsuOrganization> findByRsuIpv4AddressAndOrganization_Id(InetAddress ipv4Address,
            Integer orgId);

    @Query("SELECT ro.rsu.ipv4Address FROM RsuOrganization ro WHERE ro.organization.id = :orgId")
    List<InetAddress> findAllRsuIpsByOrganizationId(@Param("orgId") Integer orgId);

    @Query("SELECT DISTINCT r FROM Rsu r WHERE NOT EXISTS " +
            "(SELECT 1 FROM RsuOrganization ro WHERE ro.rsu.id = r.id AND ro.organization.id = :orgId)")
    List<Rsu> findAllRsusNotInOrganizationId(
            @Param("orgId") Integer orgId);

    @Modifying
    @Transactional
    @Query("DELETE FROM RsuOrganization ro WHERE ro.rsu.ipv4Address IN :ipv4Addresses AND ro.organization.id = :orgId")
    void deleteByRsuIpv4AddressesAndOrganizationId(@Param("ipv4Addresses") List<InetAddress> ipv4Addresses,
            @Param("orgId") Integer orgId);

    @Query("SELECT CASE WHEN COUNT(ro) > 0 THEN true ELSE false END "
            + "FROM RsuOrganization ro "
            + "WHERE ro.organization.id = :orgId "
            + "AND (SELECT COUNT(ro2) FROM RsuOrganization ro2 WHERE ro2.rsu.id = ro.rsu.id) = 1")
    boolean existsOrphanRsuInOrganization(@Param("orgId") Integer orgId);

    @Modifying
    @Transactional
    @Query("DELETE FROM RsuOrganization ro WHERE ro.organization.id = :orgId")
    void deleteAllByOrganizationId(@Param("orgId") Integer orgId);
}
