package us.dot.its.jpo.ode.api.accessors.notifications.active_notification;

import org.springframework.data.domain.Page;
import us.dot.its.jpo.conflictmonitor.monitor.models.notifications.Notification;
import us.dot.its.jpo.ode.api.models.DataLoader;

public interface ActiveNotificationRepository extends DataLoader<Notification> {
    long count(Integer intersectionID, String notificationType, String key);

    Page<Notification> find(Integer intersectionID, String notificationType, String key,
            Integer pageNumber, int limit);

    long delete(String key);
}