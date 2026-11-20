package com.purpurflow.server.egress;

import com.purpurflow.common.protocol.Frame;
import java.util.Comparator;

/**
 * 每玩家独立优先级队列（H3）：控制帧优先出队，队列满时按优先级丢弃。
 * 队列有大小上限，保证内存上界。
 */
public final class PriorityQueue {
    private final int maxSize;
    private final java.util.PriorityQueue<Entry> queue =
            new java.util.PriorityQueue<>(Comparator.comparingInt((Entry e) -> e.priority.level())
                    .thenComparingLong(e -> e.order));

    private long order = 0;

    public record Entry(Frame frame, Priority priority, long order) {}

    public PriorityQueue(int maxSize) { this.maxSize = maxSize; }

    /** 入队。队列满时：若新帧优先级更高则替换最低优先级元素，否则丢弃返回 false。 */
    public boolean offer(Frame frame, Priority priority) {
        if (queue.size() >= maxSize) {
            // 修复 R-08：删除无用的 queue.peek()（最小堆 peek 返回最高优先级，与语义无关）。
            // 真正要替换的是"优先级数值最大（最低优先级）、同优先级最旧"的队尾元素。
            Entry tail = queue.stream()
                    .max(Comparator.comparingInt((Entry e) -> e.priority.level())
                            .thenComparingLong(Entry::order))
                    .orElse(null);
            if (tail == null || priority.level() >= tail.priority.level()) return false;
            queue.remove(tail);
        }
        queue.offer(new Entry(frame, priority, order++));
        return true;
    }

    public Entry poll() { return queue.poll(); }
    public int size() { return queue.size(); }
    public boolean isEmpty() { return queue.isEmpty(); }
}
