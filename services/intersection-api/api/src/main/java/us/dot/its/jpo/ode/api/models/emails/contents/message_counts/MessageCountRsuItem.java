package us.dot.its.jpo.ode.api.models.emails.contents.message_counts;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class MessageCountRsuItem {
    @JsonProperty("rsu_ip")
    private String rsuIp;
    @JsonProperty("primary_route")
    private String primaryRoute;
    @JsonProperty("counts")
    private Map<String, MessageCountCountsItem> messageCountsByType;
}
