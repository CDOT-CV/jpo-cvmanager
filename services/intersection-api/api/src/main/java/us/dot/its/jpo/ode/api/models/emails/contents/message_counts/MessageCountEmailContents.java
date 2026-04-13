package us.dot.its.jpo.ode.api.models.emails.contents.message_counts;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Contents of message count summary email")
@Data
public class MessageCountEmailContents {
    @Schema(description = "Name of the organization the message counts were collected for", example = "CDOT-CV")
    @JsonProperty("org_name")
    private String organizationName;
    @Schema(description = "Title of the deployment the message counts were collected for", example = "CDOT-OIM-CV-DEV")
    @JsonProperty("deployment_title")
    private String deploymentTitle;
    @Schema(description = "Start date of the message count aggregation period")
    @JsonProperty("start_date")
    private Instant startDate;
    @Schema(description = "End date of the message count aggregation period")
    @JsonProperty("end_date")
    private Instant endDate;

    @Schema(description = "List of message types included in the message counts", example = "[\"BSM\", \"SPaT\", \"MAP\"]")
    @JsonProperty("message_type_list")
    private List<String> messageTypeList;

    @Schema(description = "RSU message counts by message type and RSU")
    @JsonProperty("rsu_counts")
    private List<MessageCountRsuItem> rsuCounts; // TODO: define message count contents
}
