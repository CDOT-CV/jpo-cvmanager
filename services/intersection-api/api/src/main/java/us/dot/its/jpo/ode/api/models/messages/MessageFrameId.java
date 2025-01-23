package us.dot.its.jpo.ode.api.models.messages;

import lombok.Getter;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public enum MessageFrameId {
    MAP(0x12),
    SPAT(0x13),
    BSM(0x14),
    SRM(0x1D),
    SSM(0x1E);

    final int id;

    MessageFrameId(int id) {
        this.id = id;
    }

    public static Set<Integer> idSet() {
        return Stream.of(values()).map(MessageFrameId::getId).collect(Collectors.toUnmodifiableSet());
    }

    public static MessageFrameId fromId(int id) {
        for (MessageFrameId mfid : values()) {
            if (mfid.getId() == id) return mfid;
        }
        return null;
    }
}
