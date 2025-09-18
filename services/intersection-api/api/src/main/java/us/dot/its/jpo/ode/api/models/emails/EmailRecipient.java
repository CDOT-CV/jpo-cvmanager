package us.dot.its.jpo.ode.api.models.emails;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class EmailRecipient {
    private String email;
    private String name;

    public com.sendgrid.helpers.mail.objects.Email toSendGridEmail() {
        return new com.sendgrid.helpers.mail.objects.Email(this.email, this.name);
    }
}
