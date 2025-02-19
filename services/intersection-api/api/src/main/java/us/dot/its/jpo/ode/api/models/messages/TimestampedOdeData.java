package us.dot.its.jpo.ode.api.models.messages;

import us.dot.its.jpo.ode.model.OdeData;
import lombok.Data;

/**
 * Convenience wrapper to hold an OdeData with a timestamp
 */
@Data
public class TimestampedOdeData {
  private long timestamp;
  private MessageType type;
  private OdeData odeData;
}
