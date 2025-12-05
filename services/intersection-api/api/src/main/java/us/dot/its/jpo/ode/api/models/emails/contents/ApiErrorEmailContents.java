package us.dot.its.jpo.ode.api.models.emails.contents;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ApiErrorEmailContents {
    @JsonProperty("subject")
    private String subject;
    @JsonProperty("message")
    private String message;
}
