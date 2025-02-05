package us.dot.its.jpo.ode.api.models.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import us.dot.its.jpo.geojsonconverter.DateJsonMapper;

import java.util.ArrayList;
import java.util.Formatter;

public class TimestampedMessageFrameHexList extends ArrayList<TimestampedMessageFrameHex> {

    public TimestampedMessageFrameHexList() { super(); }

    public TimestampedMessageFrameHexList(TimestampedMessageFrameList messageFrameList) {
        for (TimestampedMessageFrame messageFrame : messageFrameList) {
            add(new TimestampedMessageFrameHex(messageFrame));
        }
    }

    public String toLineDelimitedJson() throws JsonProcessingException {
        var formatter = new Formatter();
        for (TimestampedMessageFrameHex hex : this) {
            formatter.format("%s%n", DateJsonMapper.getInstance().writeValueAsString(hex));
        }
        return formatter.toString();
    }
}
