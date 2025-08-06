package us.dot.its.jpo.ode.api.accessors.notifications.stop_line_passage_notification;

import org.springframework.data.domain.Page;
import us.dot.its.jpo.conflictmonitor.monitor.models.notifications.StopLinePassageNotification;
import us.dot.its.jpo.ode.api.models.DataLoader;

public interface StopLinePassageNotificationRepository extends DataLoader<StopLinePassageNotification> {
    long count(Integer intersectionID, Long startTime, Long endTime);

    Page<StopLinePassageNotification> findLatest(Integer intersectionID, Long startTime, Long endTime);

    Page<StopLinePassageNotification> find(Integer intersectionID, Long startTime, Long endTime, Integer pageNumber,
            int limit);
}