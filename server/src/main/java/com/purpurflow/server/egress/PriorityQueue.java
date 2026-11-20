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

    /** 入队。队列满时：若新帧优先级更高则替换队尾，否则丢弃返回 false。 */
    public boolean offer(Frame frame, Priority priority) {
        if (queue.size() >= maxSize) {
            Entry lowest = queue.peek(); // 优先级最高，不能替换
            // 找最低优先级元素（尾）
            Entry tail = queue.stream().max(Comparator.comparingInt((Entry e) -> e.priority.level())
                    .thenComparingLong(e -> e.order)).orElse(null);
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
