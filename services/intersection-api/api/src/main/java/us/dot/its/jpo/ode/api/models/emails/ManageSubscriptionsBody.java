package us.dot.its.jpo.ode.api.models.emails;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ManageSubscriptionsBody {
    private String email;
    private List<EmailCategory> categoriesToSubscribe;
}
