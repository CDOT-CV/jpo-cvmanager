package us.dot.its.jpo.ode.api.models.emails.contents.message_counts;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Message counts for a specific message type, including ingress, egress, and percentage difference between the two")
@Data
public class MessageCountCountsItem {
    @Schema(description = "Ingress message counts")
    private int in;
    @Schema(description = "Egress message counts")
    private int out;
    @Schema(description = "Percentage difference between ingress and egress message counts")
    @JsonProperty("diff_percent")
    private double diffPercent;
}
