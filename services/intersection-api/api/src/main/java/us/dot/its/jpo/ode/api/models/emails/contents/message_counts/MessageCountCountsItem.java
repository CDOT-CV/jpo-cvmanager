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
    @Schema(description = "Absolute percentage difference of outbound vs inbound counts " +
            "(|out/in - 1| * 100). 0% means identical counts. Cells with a difference greater " +
            "than 5% are highlighted as errors. When inbound is zero and outbound is not, " +
            "the value is 100%.")
    @JsonProperty("diff_percent")
    private double diffPercent;
}
