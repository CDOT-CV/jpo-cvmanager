package us.dot.its.jpo.ode.api.accessors.notifications.lane_direction_of_travel_notification;

import org.springframework.data.domain.Page;
import us.dot.its.jpo.conflictmonitor.monitor.models.notifications.LaneDirectionOfTravelNotification;
import us.dot.its.jpo.ode.api.models.DataLoader;

public interface LaneDirectionOfTravelNotificationRepository extends DataLoader<LaneDirectionOfTravelNotification> {
    long count(Integer intersectionID, Long startTime, Long endTime);

    Page<LaneDirectionOfTravelNotification> findLatest(Integer intersectionID, Long startTime, Long endTime);

    Page<LaneDirectionOfTravelNotification> find(Integer intersectionID, Long startTime, Long endTime,
            Integer pageNumber, int limit);
}