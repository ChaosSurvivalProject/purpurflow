package com.purpurflow.client.net;

import com.purpurflow.common.protocol.Flags;
import com.purpurflow.common.protocol.Frame;

import java.util.HashMap;
import java.util.Map;

/**
 * 分片重组（L1）：按 session+seq 聚合分片帧，收齐后返回完整帧。
 * 重组缓冲有大小与超时上限，超时丢弃并请求增量补发，不无限等待（H3/2.3）。
 */
public final class Reassembler {
    private static final class Part {
        // 修复 R-02：改用按 index 索引的 Map 承载分片，不再按首片 index 定长。
        // 旧实现 Part(1) 只在首片分配 1 个槽，第二片 index=1 即 >= chunks.length 被丢弃，
        // 导致任意 >2 片的分片帧永远无法收齐；且无法处理乱序到达。
        final Map<Integer, byte[]> chunks = new HashMap<>();
        int expectedTotal = -1;   // 从"最后片"的 fragIndex+1 得知，未知前为 -1
        final long createdMs = System.currentTimeMillis();

        boolean isComplete() {
            return expectedTotal > 0 && chunks.size() == expectedTotal;
        }
    }

    private static final int MAX_FRAGMENTS = 1 << 16; // 单片上限 65536，防恶意 index 撑爆内存

    private final int maxBuffered;
    private final long timeoutMs;
    private final Map<Long, Part> pending = new HashMap<>();

    public Reassembler(int maxBuffered, long timeoutMs) {
        this.maxBuffered = maxBuffered;
        this.timeoutMs = timeoutMs;
    }

    /**
     * 接受一个分片帧，返回重组完成的完整字节；未收齐返回 null。
     * key 用 session 高位 + seq 低位组合，避免冲突。
     *
     * 协议约定：每片 fragIndex 为 0..N-1，前 N-1 片带 MORE 标志，最后一片不带 MORE；
     * 最后一片到达时即可确定总数 N。支持乱序到达，收齐后按 index 顺序拼接。
     */
    public byte[] accept(Frame frame, byte[] rawFrameBytes) {
        evictExpired();
        long key = (frame.session() << 20) ^ (frame.seq() & 0xFFFFF);
        int index = Math.max(0, frame.fragIndex());
        if (index >= MAX_FRAGMENTS) return null; // 越界丢弃，防内存攻击
        boolean more = Flags.has(frame.flags(), Flags.MORE);

        Part part = pending.computeIfAbsent(key, k -> {
            if (pending.size() >= maxBuffered) evictOldest();
            return new Part();
        });

        // 去重：同一 index 重复到达直接忽略，避免计数虚高
        if (part.chunks.containsKey(index)) {
            return part.isComplete() ? finish(key, part) : null;
        }
        part.chunks.put(index, rawFrameBytes);
        if (!more) {
            // 无 MORE = 最后一片，由此确定总数
            part.expectedTotal = index + 1;
        }

        if (!part.isComplete()) return null;
        return finish(key, part);
    }

    /** 按 index 升序拼接所有分片（乱序到达也保证字节顺序正确）。 */
    private byte[] finish(long key, Part part) {
        int size = 0;
        for (int i = 0; i < part.expectedTotal; i++) {
            byte[] c = part.chunks.get(i);
            if (c == null) return null; // 有缺口，尚未真正收齐
            size += c.length;
        }
        byte[] out = new byte[size];
        int off = 0;
        for (int i = 0; i < part.expectedTotal; i++) {
            byte[] c = part.chunks.get(i);
            System.arraycopy(c, 0, out, off, c.length);
            off += c.length;
        }
        pending.remove(key);
        return out;
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> now - e.getValue().createdMs > timeoutMs);
    }

    private void evictOldest() {
        Long oldest = null;
        long min = Long.MAX_VALUE;
        for (Map.Entry<Long, Part> e : pending.entrySet()) {
            if (e.getValue().createdMs < min) { min = e.getValue().createdMs; oldest = e.getKey(); }
        }
        if (oldest != null) pending.remove(oldest);
    }
}
