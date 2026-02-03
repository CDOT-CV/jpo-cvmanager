package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuOrganization;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;

@Repository
public interface RsuOrganizationRepository extends JpaRepository<RsuOrganization, Integer> {

    @Transactional
    @Query("DELETE FROM RsuOrganization ro WHERE ro.rsu.ipv4Address = :ipv4Address")
    void removeRsuOrganizationByIpv4Address(@Param("ipv4Address") InetAddress ipv4Address);

    @Transactional
    @Query("DELETE FROM RsuOrganization ro WHERE ro.rsu.ipv4Address IN :ipv4Addresses")
    void removeMultipleRsuOrganizationsByIpv4Address(@Param("ipv4Addresses") List<InetAddress> ipv4Addresses);

    @Query("SELECT ro FROM RsuOrganization ro WHERE ro.rsu.ipv4Address = :ipv4Address")
    List<RsuOrganization> findAllByIpv4Address(@Param("ipv4Address") InetAddress ipv4Address);

    Optional<RsuOrganization> findByOrganizationName(String organizationName);
}
