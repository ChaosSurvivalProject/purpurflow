package com.purpurflow.common.protocol;

/**
 * 布尔与小枚举位打包工具。把多个 1 位布尔压进一个 long 的位图，用于脏位图/能力位图。
 */
public final class BitPacker {
    private BitPacker() {}

    /** 用一个 long 位图表示最多 64 个布尔位。 */
    public static long pack(boolean... bits) {
        long out = 0;
        for (int i = 0; i < bits.length; i++) {
            if (bits[i]) out |= (1L << i);
        }
        return out;
    }

    public static boolean get(long bitmap, int index) {
        if (index < 0 || index >= 64) throw new IllegalArgumentException("bit index 越界: " + index);
        return (bitmap & (1L << index)) != 0;
    }

    public static long set(long bitmap, int index, boolean value) {
        if (index < 0 || index >= 64) throw new IllegalArgumentException("bit index 越界: " + index);
        return value ? (bitmap | (1L << index)) : (bitmap & ~(1L << index));
    }

    /** 统计置位数量（脏字段个数），用于估算 delta 体积。 */
    public static int count(long bitmap) {
        return Long.bitCount(bitmap);
    }
}
