package us.dot.its.jpo.ode.api.accessors.rsuState;

import java.util.List;
import us.dot.its.jpo.ode.api.models.snmp.RsuState;
import us.dot.its.jpo.ode.api.models.DataLoader;

public interface RsuStateRepository extends DataLoader<RsuState> {
    List<RsuState> findByRsuIPAndTimestampBetween(String rsuIP, long start, long end);

    List<RsuState> findByRsuIPOrderByTimestampDesc(String rsuIP);
}