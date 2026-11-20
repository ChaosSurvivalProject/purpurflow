package com.purpurflow.server.egress;

import com.purpurflow.common.protocol.Frame;

/**
 * 出站编排器（H3）：把此前分散未接线的背压/限速/优先级队列/可写性门控编排成一条链。
 *
 * 每玩家一个实例（持有各自的队列与令牌桶）。流程：
 *   1) offer(frame, priority, bytes, sink) —— 帧连同"写回闭包"进优先级队列（有上界，按优先级丢弃）；
 *   2) drain(maxFrames) —— 逐帧出队，经速率令牌桶校验后调用 sink 写回；限速未通过的帧
 *      放回队首，下个 tick 再试（不丢）。sink 捕获具体 player/channel，故本类不依赖 Bukkit。
 *
 * H3 四要素全部落地：独立队列 + 队列上限 + 优先级丢弃 + 速率上限；可写性由 WritabilityProbe
 * 在 offer 时门控（探测 channel.isWritable，由上层用实际 Netty 状态实现），H1 无额外端口。
 */
public final class EgressPipeline {

    @FunctionalInterface
    public interface Sink {
        /** 返回 true 表示成功写出；false 表示 channel 关闭等不可恢复错误。 */
        boolean send(Frame frame);
    }

    @FunctionalInterface
    public interface FrameSize {
        int bytes(Frame frame);
    }

    private record Entry(Frame frame, Priority priority, Sink sink, long order) {}

    private final int maxQueue;
    private final RateLimiter limiter;
    private final BackpressureGate gate;
    private final java.util.PriorityQueue<Entry> queue = new java.util.PriorityQueue<>(
            java.util.Comparator.<Entry>comparingInt(e -> e.priority.level())
                    .thenComparingLong(e -> e.order));

    private long order = 0;
    private long droppedByBackpressure = 0;
    private long droppedByFullQueue = 0;
    private long delayedByRate = 0;
    private long sentTotal = 0;

    public EgressPipeline(int maxQueue, long maxBytesPerSec, int maxPacketsPerSec,
                          int highWatermark, BackpressureGate.WritabilityProbe probe) {
        this.maxQueue = maxQueue;
        this.limiter = new RateLimiter(maxBytesPerSec, maxPacketsPerSec);
        this.gate = new BackpressureGate(highWatermark, probe);
    }

    /** 入队：先过可写性/高水位门控，再按优先级入队（队满按优先级丢弃）。 */
    public boolean offer(Frame frame, Priority priority, int estimatedBytes, Object player, Sink sink) {
        if (!gate.canEmit(player, queue.size() + 1)) {
            droppedByBackpressure++;
            return false;
        }
        if (queue.size() >= maxQueue) {
            Entry tail = queue.stream()
                    .max(java.util.Comparator.<Entry>comparingInt(e -> e.priority.level())
                            .thenComparingLong(e -> e.order))
                    .orElse(null);
            if (tail == null || priority.level() >= tail.priority.level()) {
                droppedByFullQueue++;
                return false;
            }
            queue.remove(tail);
        }
        queue.offer(new Entry(frame, priority, sink, order++));
        return true;
    }

    /** 排出至多 maxFrames 帧，返回实际写出数。限速未通过则放回队首并停止本轮。 */
    public int drain(int maxFrames, FrameSize sizeOf) {
        int sent = 0;
        for (int i = 0; i < maxFrames && !queue.isEmpty(); i++) {
            Entry head = queue.poll();
            if (!limiter.tryAcquire(sizeOf.bytes(head.frame()))) {
                queue.offer(head); // 保留原始 order，放回队首
                delayedByRate++;
                break;
            }
            if (!head.sink().send(head.frame())) {
                droppedByBackpressure++; // channel 关闭，丢弃避免死循环
                continue;
            }
            sent++;
            sentTotal++;
        }
        return sent;
    }

    public int queueSize() { return queue.size(); }
    public long droppedByBackpressure() { return droppedByBackpressure; }
    public long droppedByFullQueue() { return droppedByFullQueue; }
    public long delayedByRate() { return delayedByRate; }
    public long sentTotal() { return sentTotal; }
    public void resetCounters() {
        droppedByBackpressure = 0;
        droppedByFullQueue = 0;
        delayedByRate = 0;
        sentTotal = 0;
    }
}
