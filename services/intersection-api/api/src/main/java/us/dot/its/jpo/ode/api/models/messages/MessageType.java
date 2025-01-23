package us.dot.its.jpo.ode.api.models.messages;

import lombok.Getter;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public enum MessageType {
    UNKNOWN(0),
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

    public static Set<Integer> idSet() {
        return Stream.of(values())
                .filter(type -> type != MessageType.UNKNOWN)
                .map(MessageType::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static MessageType fromId(int id) {
        for (MessageType tid : values()) {
            if (tid.getId() == id) return tid;
        }
        return null;
    }
}
