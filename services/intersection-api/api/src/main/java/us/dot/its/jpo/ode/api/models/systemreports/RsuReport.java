package us.dot.its.jpo.ode.api.models.systemreports;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Setter
@EqualsAndHashCode
@Getter
public class RsuReport {

    Long mapMessageCount;
    Long spatMessageCount;
    Long timMessageCount;
    Long bsmMessageCount;
    Long sdsmMessageCount;
    Long rtcmMessageCount;

    List<RsuReceivedMessage<?>> receivedMessages;

}
