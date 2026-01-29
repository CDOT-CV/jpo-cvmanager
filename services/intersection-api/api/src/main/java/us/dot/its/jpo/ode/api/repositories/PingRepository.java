package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import us.dot.its.jpo.ode.api.models.postgres.tables.Ping;

import java.net.InetAddress;

@Repository
public interface PingRepository extends JpaRepository<Ping, Integer> {

    @Transactional
    @Query("DELETE FROM Ping ro WHERE ro.rsu.ipv4Address = :ipv4Address")
    void removePingByIpv4Address(@Param("ipv4Address") InetAddress ipv4Address);
}
