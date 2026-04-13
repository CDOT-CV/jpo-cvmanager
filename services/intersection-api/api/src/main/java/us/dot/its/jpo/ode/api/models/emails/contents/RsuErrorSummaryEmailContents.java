package us.dot.its.jpo.ode.api.models.emails.contents;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Contents of RSU error summary email, including a list of recipient email addresses, email subject, and email message body that contains details about recent RSU errors")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RsuErrorSummaryEmailContents {
    @Schema(description = "List of email addresses of recipients to email rsu error summary to")
    @JsonProperty("recipients")
    private List<String> recipients;
    @Schema(description = "Email subject")
    @JsonProperty("subject")
    private String subject;
    @Schema(description = "Email message body")
    @JsonProperty("message")
    private String message;
}
