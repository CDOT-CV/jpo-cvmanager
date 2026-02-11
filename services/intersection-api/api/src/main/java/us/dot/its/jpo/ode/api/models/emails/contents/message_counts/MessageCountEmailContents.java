package us.dot.its.jpo.ode.api.models.emails.contents.message_counts;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class MessageCountEmailContents {
    @JsonProperty("org_name")
    private String organizationName;
    @JsonProperty("deployment_title")
    private String deploymentTitle;
    @JsonProperty("start_date")
    private Instant startDate;
    @JsonProperty("end_date")
    private Instant endDate;

    @JsonProperty("message_type_list")
    private List<String> messageTypeList;

    @JsonProperty("rsu_counts")
    private List<MessageCountRsuItem> rsuCounts; // TODO: define message count contents
}
