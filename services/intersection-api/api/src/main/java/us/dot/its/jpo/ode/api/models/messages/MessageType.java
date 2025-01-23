package us.dot.its.jpo.ode.api.models.messages;

import lombok.Getter;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public enum MessageType {
    UNKNOWN(-1),
    BSM(0x14),
    MAP(0x12),
    SPAT(0x13),
    SRM(0x1D),
    SSM(0x1E),
    TIM(0x1F);

    final int id;

    MessageType(int id) {
        this.id = id;
    }

    /**
     * Set of message IDs, excluding "UNKNOWN"
      */
    public static final Set<Integer> MESSAGE_FRAME_IDS = MessageType.idSet();

    private static Set<Integer> idSet() {
        return Stream.of(values())
                .filter(type -> type != MessageType.UNKNOWN)
                .map(MessageType::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static MessageType fromId(int id) {
        if (!MESSAGE_FRAME_IDS.contains(id)) return null;
        for (MessageType tid : values()) {
            if (tid.getId() == id)  return tid;
        }
        return null;
    }
}
