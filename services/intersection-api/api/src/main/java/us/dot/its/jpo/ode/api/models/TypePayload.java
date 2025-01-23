package us.dot.its.jpo.ode.api.models;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import us.dot.its.jpo.ode.api.models.messages.MessageType;

@ToString
@Setter
@EqualsAndHashCode
@Getter
@AllArgsConstructor
public class TypePayload {
    private MessageType type;
    private String payload;
}