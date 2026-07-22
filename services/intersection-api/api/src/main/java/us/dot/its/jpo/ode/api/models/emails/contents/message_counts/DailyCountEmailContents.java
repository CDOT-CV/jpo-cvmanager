package us.dot.its.jpo.ode.api.models.emails.contents.message_counts;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.Data;
import us.dot.its.jpo.ode.api.models.MessageCount;

@Data
public class DailyCountEmailContents {
    private String organizationName;
    private String deploymentTitle;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private Map<String, List<MessageCount>> rsuCountsByOrganization;
    private List<String> messageTypes;
}