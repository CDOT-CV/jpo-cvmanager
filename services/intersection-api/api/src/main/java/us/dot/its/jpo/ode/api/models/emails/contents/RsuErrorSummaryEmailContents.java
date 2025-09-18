package us.dot.its.jpo.ode.api.models.emails.contents;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class RsuErrorSummaryEmailContents {
    @JsonProperty("recipients")
    private List<String> recipients;
    @JsonProperty("subject")
    private String subject;
    @JsonProperty("message")
    private String message;
}
