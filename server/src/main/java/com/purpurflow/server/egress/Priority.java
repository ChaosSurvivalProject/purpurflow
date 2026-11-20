package com.purpurflow.server.egress;

/** 出站优先级：控制帧 > 关键状态 > 可丢弃遥测（H3）。数值越小越高。 */
public enum Priority {
    CONTROL(0),
    STATE(1),
    TELEMETRY(2);

    private final int level;

    Priority(int level) { this.level = level; }

    public int level() { return level; }
}
