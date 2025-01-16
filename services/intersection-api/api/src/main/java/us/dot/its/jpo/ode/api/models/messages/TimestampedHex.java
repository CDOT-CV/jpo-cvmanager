package us.dot.its.jpo.ode.api.models.messages;

import java.io.IOException;
import java.util.HexFormat;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import lombok.Data;
import lombok.Generated;
import us.dot.its.jpo.ode.api.serialization.HexDeserializer;
import us.dot.its.jpo.ode.api.serialization.HexSerializer;

/**
 * A Timestamped Hex message decoded from a PCAP packet
 */
@Data
@Generated
public class TimestampedHex {
    
    long timestamp;

    @JsonSerialize(using = HexSerializer.class)
    @JsonDeserialize(using = HexDeserializer.class)
    byte[] message;

}
