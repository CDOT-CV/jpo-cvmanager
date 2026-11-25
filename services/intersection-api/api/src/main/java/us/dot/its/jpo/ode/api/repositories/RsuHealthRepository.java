package us.dot.its.jpo.ode.api.repositories;

import java.net.InetAddress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import us.dot.its.jpo.ode.api.models.postgres.tables.RsuHealth;

@Repository
public interface RsuHealthRepository extends JpaRepository<RsuHealth, Integer> {

    @Modifying
    @Transactional
    @Query("DELETE FROM RsuHealth rh WHERE rh.rsu.ipv4Address = :ipv4Address")
    void removeRsuHealthByIpv4Address(@Param("ipv4Address") InetAddress ipv4Address);

    @Modifying
    @Transactional
    @Query("DELETE FROM RsuHealth rh WHERE rh.rsu.ipv4Address IN :ipv4Addresses")
    void removeMultipleRsuHealthByIpv4Address(@Param("ipv4Addresses") java.util.List<InetAddress> ipv4Addresses);
}
