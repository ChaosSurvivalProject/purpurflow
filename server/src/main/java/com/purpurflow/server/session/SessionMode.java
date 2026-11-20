package com.purpurflow.server.session;

/** 会话模式：握手完成前的未知、增强流开启、vanilla 降级。 */
public enum SessionMode {
    HANDSHAKING,
    ENHANCED,
    VANILLA
}
