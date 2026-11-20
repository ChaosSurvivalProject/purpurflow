package com.purpurflow.common.protocol;

/**
 * 帧 flags(u8) 位定义。
 * bit0 MORE, bit1 FRAG, bit2 COMPRESSED, bit3 CRC,
 * bit4-5 RELIABILITY, bit6-7 PRIORITY。
 */
public final class Flags {
    public static final int MORE = 1 << 0;
    public static final int FRAG = 1 << 1;
    public static final int COMPRESSED = 1 << 2;
    public static final int CRC = 1 << 3;

    // reliability (bits 4-5)
    public static final int RELIABILITY_MASK = 0b11 << 4;
    public static final int REL_DROPPABLE = 0b00 << 4;
    public static final int REL_BEST_EFFORT = 0b01 << 4;
    public static final int RELIABLE_ORDERED = 0b10 << 4;

    // priority (bits 6-7)
    public static final int PRIORITY_MASK = 0b11 << 6;
    public static final int PRI_CONTROL = 0b00 << 6;
    public static final int PRI_STATE = 0b01 << 6;
    public static final int PRI_TELEMETRY = 0b10 << 6;

    private Flags() {}

    public static boolean has(int flags, int bit) {
        return (flags & bit) != 0;
    }

    public static int reliability(int flags) {
        return flags & RELIABILITY_MASK;
    }

    public static int priority(int flags) {
        return flags & PRIORITY_MASK;
    }

    public static int with(int flags, int bits) {
        return flags | bits;
    }

    public static int without(int flags, int bits) {
        return flags & ~bits;
    }
}
