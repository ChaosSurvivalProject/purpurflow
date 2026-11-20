package com.purpurflow.common.protocol;

import java.util.Arrays;
import java.util.Objects;

/** 帧内的一条逻辑消息：类型 + 载荷字节。 */
public final class Message {
    private final FrameType type;
    private final byte[] payload;

    public Message(FrameType type, byte[] payload) {
        this.type = Objects.requireNonNull(type, "type");
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    public FrameType type() {
        return type;
    }

    public byte[] payload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message other)) return false;
        return type == other.type && Arrays.equals(payload, other.payload);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + Arrays.hashCode(payload);
    }
}
