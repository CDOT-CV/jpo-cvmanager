package us.dot.its.jpo.ode.api.models.emails;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class EmailWrapper {
    private String recipientEmail;
    private String subject;
    private String body;
    private String unsubscribeUrl;
}
