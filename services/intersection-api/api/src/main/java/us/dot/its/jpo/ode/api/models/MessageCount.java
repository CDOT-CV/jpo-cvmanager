package us.dot.its.jpo.ode.api.models;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageCount {

    @JsonProperty("messageType")
    private String messageType;

    @JsonProperty("rsuIp")
    private String rsuIp;

    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    @JsonProperty("count")
    private Long count;

    @JsonProperty("source")
    private String source;

    @JsonProperty("countType")
    private String countType;
}