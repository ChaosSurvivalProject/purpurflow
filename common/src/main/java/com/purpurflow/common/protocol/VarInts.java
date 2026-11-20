package com.purpurflow.common.protocol;

import java.nio.ByteBuffer;

/**
 * VarInt / VarLong / ZigZag 编解码工具，零 MC 依赖。
 * 布局与 Minecraft 协议一致：每字节低 7 位是数据，最高位是继续位。
 */
public final class VarInts {
    private VarInts() {}

    /** 写入 int 的 VarInt 编码（无符号语义，最高位为符号延伸）。 */
    public static void writeVarInt(ByteBuffer buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        buf.put((byte) value);
    }

    /** 读取 VarInt，最多 5 字节。 */
    public static int readVarInt(ByteBuffer buf) {
        int value = 0;
        int size = 0;
        byte b;
        do {
            if (size >= 5) throw new FrameException("VarInt 超过 5 字节");
            b = buf.get();
            value |= (b & 0x7F) << (size++ * 7);
        } while ((b & 0x80) != 0);
        return value;
    }

    /** 写入 long 的 VarLong 编码。 */
    public static void writeVarLong(ByteBuffer buf, long value) {
        while ((value & ~0x7FL) != 0) {
            buf.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        buf.put((byte) value);
    }

    /** 读取 VarLong，最多 10 字节。 */
    public static long readVarLong(ByteBuffer buf) {
        long value = 0;
        int size = 0;
        byte b;
        do {
            if (size >= 10) throw new FrameException("VarLong 超过 10 字节");
            b = buf.get();
            value |= ((long) (b & 0x7F)) << (size++ * 7);
        } while ((b & 0x80) != 0);
        return value;
    }

    /** ZigZag：有符号小幅值映射到无符号，小负数编码更省字节。 */
    public static int zigZagEncode(int value) {
        return (value << 1) ^ (value >> 31);
    }

    public static int zigZagDecode(int encoded) {
        return (encoded >>> 1) ^ -(encoded & 1);
    }

    public static long zigZagEncode(long value) {
        return (value << 1) ^ (value >> 63);
    }

    public static long zigZagDecode(long encoded) {
        return (encoded >>> 1) ^ -(encoded & 1);
    }

    /** 计算某 int 以 VarInt 编码所需字节数，用于预留缓冲。 */
    public static int sizeOfVarInt(int value) {
        int n = 1;
        while ((value & ~0x7F) != 0) {
            n++;
            value >>>= 7;
        }
        return n;
    }
}
