package us.dot.its.jpo.ode.api.models.emails.contents;

import lombok.Data;

@Data
public class SupportRequestEmailContents {
    private String userEmail;
    private String organization;
    private String message;
}
