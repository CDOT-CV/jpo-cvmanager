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

    @JsonProperty("message_type")
    private String messageType;

    @JsonProperty("rsu_ip")
    private String rsuIp;

    @JsonProperty("ode_input_count")
    private Long odeInputCount;

    @JsonProperty("ode_output_count")
    private Long odeOutputCount;

    @JsonProperty("road")
    private String road;
}