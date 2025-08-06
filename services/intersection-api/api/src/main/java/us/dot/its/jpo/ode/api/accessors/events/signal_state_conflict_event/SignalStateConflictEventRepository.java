
package us.dot.its.jpo.ode.api.accessors.events.signal_state_conflict_event;

import java.util.List;

import org.springframework.data.domain.Page;

import us.dot.its.jpo.conflictmonitor.monitor.models.events.SignalStateConflictEvent;
import us.dot.its.jpo.ode.api.models.IDCount;
import us.dot.its.jpo.ode.api.models.DataLoader;

public interface SignalStateConflictEventRepository extends DataLoader<SignalStateConflictEvent> {
    long count(Integer intersectionID, Long startTime, Long endTime);

    Page<SignalStateConflictEvent> findLatest(Integer intersectionID, Long startTime, Long endTime);

    Page<SignalStateConflictEvent> find(Integer intersectionID, Long startTime, Long endTime, Integer pageNumber,
            int limit);

    List<IDCount> getAggregatedDailySignalStateConflictEventCounts(int intersectionID, Long startTime, Long endTime);
}
