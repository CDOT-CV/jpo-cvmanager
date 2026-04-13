package us.dot.its.jpo.ode.api.models.emails.contents;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Contents of support request email, including email address of the user submitting the support request, email subject, and email message body")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SupportRequestEmailContents {
    @Schema(description = "Email address of the user submitting the support request")
    private String email;
    @Schema(description = "Email subject")
    private String subject;
    @Schema(description = "Email message body")
    private String message;
}
