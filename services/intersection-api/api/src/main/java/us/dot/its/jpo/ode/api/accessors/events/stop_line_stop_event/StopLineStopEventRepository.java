
package us.dot.its.jpo.ode.api.accessors.events.stop_line_stop_event;

import java.util.List;

import org.springframework.data.domain.Page;
import us.dot.its.jpo.conflictmonitor.monitor.models.events.StopLineStopEvent;
import us.dot.its.jpo.ode.api.models.IDCount;
import us.dot.its.jpo.ode.api.models.DataLoader;

public interface StopLineStopEventRepository extends DataLoader<StopLineStopEvent> {
    long count(Integer intersectionID, Long startTime, Long endTime);

    Page<StopLineStopEvent> findLatest(Integer intersectionID, Long startTime, Long endTime);

    Page<StopLineStopEvent> find(Integer intersectionID, Long startTime, Long endTime, Integer pageNumber, int limit);

    List<IDCount> getAggregatedDailyStopLineStopEventCounts(int intersectionID, Long startTime, Long endTime);
}
