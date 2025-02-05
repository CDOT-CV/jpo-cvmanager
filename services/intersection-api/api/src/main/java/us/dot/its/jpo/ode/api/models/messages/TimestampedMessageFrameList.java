package us.dot.its.jpo.ode.api.models.messages;

import java.util.ArrayList;
import java.util.Formatter;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Generated;
import us.dot.its.jpo.geojsonconverter.DateJsonMapper;

/**
 * A list of {@link TimestampedMessageFrame}s decoded from a PCAP file.
 */
public class TimestampedMessageFrameList extends ArrayList<TimestampedMessageFrame> {

    public String toLineDelimitedJson() throws JsonProcessingException {
        var formatter = new Formatter();
        for (TimestampedMessageFrame hex : this) {
            formatter.format("%s%n", DateJsonMapper.getInstance().writeValueAsString(hex));
        }
        return formatter.toString();
    }

}
