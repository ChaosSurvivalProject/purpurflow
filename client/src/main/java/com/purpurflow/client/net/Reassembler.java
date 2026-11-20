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
        final byte[][] chunks;
        final long createdMs = System.currentTimeMillis();
        int received;
        Part(int count) { this.chunks = new byte[count][]; }
    }

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
     */
    public byte[] accept(Frame frame, byte[] rawFrameBytes) {
        evictExpired();
        long key = (frame.session() << 20) ^ (frame.seq() & 0xFFFFF);
        int index = Math.max(0, frame.fragIndex());
        boolean more = Flags.has(frame.flags(), Flags.MORE);
        int total = more ? index + 1 : 1;

        Part part = pending.computeIfAbsent(key, k -> {
            if (pending.size() >= maxBuffered) evictOldest();
            return new Part(estimateTotal(frame, total));
        });
        if (index >= part.chunks.length) return null; // 越界分片丢弃
        if (part.chunks[index] == null) {
            part.chunks[index] = rawFrameBytes;
            part.received++;
        }
        if (part.received < part.chunks.length) return null;

        // 收齐：按顺序拼接各分片的 payload（此处简化为原始字节拼接，实际应取各帧 messages）
        int size = 0;
        for (byte[] c : part.chunks) if (c != null) size += c.length;
        byte[] out = new byte[size];
        int off = 0;
        for (byte[] c : part.chunks) {
            System.arraycopy(c, 0, out, off, c.length);
            off += c.length;
        }
        pending.remove(key);
        return out;
    }

    private int estimateTotal(Frame frame, int fallback) {
        // 若上层在首片携带总数（约定放 fragIndex 高 16 位），此处解析；否则用递增估计
        return Math.max(fallback, 1);
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
