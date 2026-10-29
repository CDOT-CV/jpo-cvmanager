package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdConfig;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpMsgfwdConfigId;

@Repository
public interface SnmpMsgfwdConfigRepository extends JpaRepository<SnmpMsgfwdConfig, SnmpMsgfwdConfigId> {

}
