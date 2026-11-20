package com.purpurflow.common.state;

/**
 * 单调递增序号追踪器（每会话独立）。seq 用于幂等去重与 ack 捎带。
 */
public final class SeqTracker {
    private long next;

    public SeqTracker() { this(0L); }
    public SeqTracker(long initial) { this.next = initial; }

    /** 取下一个 seq 并自增。 */
    public synchronized long next() { return next++; }

    /** 当前最大值（最后一个已分配 seq）。 */
    public synchronized long current() { return next - 1; }

    public synchronized void reset(long value) { this.next = value; }
}
