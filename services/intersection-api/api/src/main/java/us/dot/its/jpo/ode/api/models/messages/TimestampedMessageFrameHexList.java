package us.dot.its.jpo.ode.api.models.messages;

import java.util.ArrayList;

public class TimestampedMessageFrameHexList extends ArrayList<TimestampedMessageFrameHex> {

    public TimestampedMessageFrameHexList() { super(); }

    public TimestampedMessageFrameHexList(TimestampedMessageFrameList messageFrameList) {
        for (TimestampedMessageFrame messageFrame : messageFrameList) {
            add(new TimestampedMessageFrameHex(messageFrame));
        }
    }
}
