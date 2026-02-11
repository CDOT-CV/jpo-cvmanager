package us.dot.its.jpo.ode.api.models.emails.contents.message_counts;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class MessageCountCountsItem {
    private int in;
    private int out;
    @JsonProperty("diff_percent")
    private double diffPercent;
}
