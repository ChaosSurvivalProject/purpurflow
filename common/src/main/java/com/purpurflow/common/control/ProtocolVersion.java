package com.purpurflow.common.control;

/** 协议版本枚举，白名单校验用。DISABLED 表示协商失败。 */
public enum ProtocolVersion {
    V1(1),
    DISABLED(0);

    private final int id;

    ProtocolVersion(int id) { this.id = id; }

    public int id() { return id; }

    public static ProtocolVersion fromId(int id) {
        for (ProtocolVersion v : values()) if (v.id == id) return v;
        return DISABLED;
    }
}
