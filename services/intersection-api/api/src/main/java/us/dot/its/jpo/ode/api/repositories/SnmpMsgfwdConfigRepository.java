package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdConfig;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdConfigId;

import java.net.InetAddress;
import java.util.List;

@Repository
public interface SnmpMsgfwdConfigRepository extends JpaRepository<SnmpMsgfwdConfig, SnmpMsgfwdConfigId> {

    @Query("SELECT c FROM SnmpMsgfwdConfig c JOIN FETCH c.msgfwdType WHERE c.rsu.ipv4Address = :ipv4Address "
            + "ORDER BY c.msgfwdType.name, c.id.snmpIndex")
    List<SnmpMsgfwdConfig> findForwardingByIpv4Address(@Param("ipv4Address") InetAddress ipv4Address);

    @Modifying
    @Transactional
    @Query("DELETE FROM SnmpMsgfwdConfig ro WHERE ro.rsu.ipv4Address = :ipv4Address")
    void removeSnmpMsgfwdConfigByIpv4Address(@Param("ipv4Address") InetAddress ipv4Address);

    @Modifying
    @Transactional
    @Query("DELETE FROM SnmpMsgfwdConfig ro WHERE ro.rsu.ipv4Address IN :ipv4Addresses")
    void removeMultipleSnmpMsgfwdConfigByIpv4Address(@Param("ipv4Addresses") List<InetAddress> ipv4Addresses);
}
