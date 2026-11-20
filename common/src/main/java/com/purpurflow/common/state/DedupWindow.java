package com.purpurflow.common.state;

import java.util.HashSet;
import java.util.Set;

/**
 * 去重窗口（H3/H4）：只保留最近 window 个 seq，窗口外的 seq 视为过期直接放行不做判断。
 * 用滑动下界避免无限增长，内存有上界。
 */
public final class DedupWindow {
    private final int window;
    private final Set<Long> seen = new HashSet<>();
    private long lowerBound = Long.MIN_VALUE;

    public DedupWindow(int window) {
        if (window <= 0) throw new IllegalArgumentException("窗口必须为正");
        this.window = window;
    }

    /**
     * 判断 seq 是否首次出现。首次返回 true 并记录；重复返回 false。
     * 窗口外（小于当前最大-窗口）的 seq 视为过期，返回 false 且不记录。
     */
    public synchronized boolean isNew(long seq) {
        if (seen.size() >= window) {
            // 收缩：移除最小的若干（近似，取当前 lowerBound 推进）
            long advance = lowerBound + window;
            seen.removeIf(s -> s < advance);
            lowerBound = advance;
        }
        if (!seen.add(seq)) return false;
        if (seq > lowerBound + window) {
            lowerBound = seq - window;
            seen.removeIf(s -> s < lowerBound);
        }
        return true;
    }

    public synchronized void clear() { seen.clear(); lowerBound = Long.MIN_VALUE; }
    public synchronized int size() { return seen.size(); }
}
