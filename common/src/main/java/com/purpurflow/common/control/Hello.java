package com.purpurflow.common.control;

/**
 * HELLO（C→S）：客户端上行，必须极简（上行仅 32 KiB 限）。
 * 载荷：协议版本 + 能力位图 + 字典版本 + 本地分片上限。
 */
public final class Hello {
    private final Capability capability;

    public Hello(Capability capability) { this.capability = capability; }

    public Capability capability() { return capability; }
}
