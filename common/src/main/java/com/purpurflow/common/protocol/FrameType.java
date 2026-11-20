package com.purpurflow.common.protocol;

/**
 * 消息类型（帧内每条 (type,len,payload) 的 type）。
 * 0x00–0x1F 为控制面（白名单固定 schema），0x20+ 为业务类型（由字典映射）。
 */
public enum FrameType {
    // ---- 控制面（固定 id，白名单）----
    HELLO(0x01),
    HELLO_ACK(0x02),
    DICT_SYNC(0x03),
    DICT_SYNC_ACK(0x04),
    HEARTBEAT(0x05),
    RESUME(0x06),
    FULL_SNAPSHOT(0x07),
    DELTA(0x08),
    DISABLED(0x09),

    // ---- 业务面 ----
    RAW(0x20);

    private final int id;

    FrameType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static FrameType fromId(int id) {
        for (FrameType t : values()) {
            if (t.id == id) return t;
        }
        // 业务类型未知时走 RAW，由上层按字典二次分派（白名单之外不反序列化任意类型）
        if (id >= 0x20 && id <= 0x7F) return RAW;
        throw new FrameException("未知消息类型 id=" + id);
    }
}
