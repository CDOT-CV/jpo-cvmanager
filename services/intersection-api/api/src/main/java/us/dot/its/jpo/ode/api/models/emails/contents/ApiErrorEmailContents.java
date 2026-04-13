package us.dot.its.jpo.ode.api.models.emails.contents;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Contents of API error email, including error message, stack trace, timestamp, and link to application logs related to the error")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiErrorEmailContents {
    @Schema(description = "Exception error message")
    @JsonProperty("error_message")
    private String errorMessage;
    @Schema(description = "Exception stack trace")
    @JsonProperty("stack_trace")
    private String stackTrace;
    @Schema(description = "Timestamp of the error")
    @JsonProperty("timestamp")
    private Instant timestamp;
    @Schema(description = "Link to access application logs related to the error")
    @JsonProperty("logs_link")
    private String logsLink;
}
