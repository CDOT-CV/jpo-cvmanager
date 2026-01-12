package us.dot.its.jpo.ode.api.models.emails;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import us.dot.its.jpo.ode.api.models.postgres.derived.EmailSubscription;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class EmailSubscriptionUpdateRequest {
    private List<EmailSubscription> subscriptions;
    private String email;
}
