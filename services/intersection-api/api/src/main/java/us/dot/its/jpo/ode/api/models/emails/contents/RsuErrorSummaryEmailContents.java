package us.dot.its.jpo.ode.api.models.emails.contents;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;
import us.dot.its.jpo.ode.api.models.emails.contents.serialization.InstantFromMillisDeserializer;
import us.dot.its.jpo.ode.api.models.emails.contents.serialization.InstantToMillisSerializer;

@Data
public class RsuErrorSummaryEmailContents {
    @JsonProperty("rsu_ip")
    private String rsuIp;
    @JsonSerialize(using = InstantToMillisSerializer.class)
    @JsonDeserialize(using = InstantFromMillisDeserializer.class)
    @JsonProperty("timestamp")
    private Instant timestamp;
    @JsonProperty("online_status")
    private String onlineStatus;
    @JsonProperty("scms_status")
    private String scmsStatus;
    @JsonProperty("certificate_status")
    private String certificateStatus;
}
