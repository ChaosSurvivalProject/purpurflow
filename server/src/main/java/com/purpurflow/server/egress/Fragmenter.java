package com.purpurflow.server.egress;

/**
 * 分片器（L1）：大帧按运行时探测的 chunk 大小切分。
 * 上行 32 KiB / 下行 1 MiB 上限不同，chunk 由握手协商得出，不硬编码（H1/2.3）。
 */
public final class Fragmenter {
    /** 把一个字节数组切成若干片，每片带 index/MORE 标志由上层填充。 */
    public static Fragment[] split(byte[] data, int chunkBytes) {
        if (chunkBytes <= 0) throw new IllegalArgumentException("chunk 必须为正（运行时未探测到有效值）");
        int count = (data.length + chunkBytes - 1) / chunkBytes;
        Fragment[] out = new Fragment[count];
        for (int i = 0; i < count; i++) {
            int from = i * chunkBytes;
            int to = Math.min(data.length, from + chunkBytes);
            byte[] part = new byte[to - from];
            System.arraycopy(data, from, part, 0, part.length);
            out[i] = new Fragment(part, i, i < count - 1);
        }
        return out;
    }

    public record Fragment(byte[] data, int index, boolean more) {}
}
