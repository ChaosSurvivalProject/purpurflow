package com.purpurflow.common.state;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 状态增量：仅含变更字段，带 baseSeq 用于重连增量补发。
 * 可靠性分级由帧 flags 表达，delta 本身只描述数据差异。
 */
public final class Delta {
    public record Entry(long value, long version) {}

    private final long baseSeq;
    private final Map<Integer, Entry> entries = new LinkedHashMap<>();
    private final DirtyBitmap dirty = new DirtyBitmap();

    public Delta(long baseSeq) { this.baseSeq = baseSeq; }

    public void put(int key, long value, long version) {
        entries.put(key, new Entry(value, version));
        dirty.mark(key);
    }

    public long baseSeq() { return baseSeq; }
    public Map<Integer, Entry> entries() { return entries; }
    public DirtyBitmap dirty() { return dirty; }
    public boolean isEmpty() { return entries.isEmpty(); }

    /** 合并另一 delta（背压时合并可丢弃更新），高版本号胜出。 */
    public void merge(Delta other) {
        for (Map.Entry<Integer, Entry> e : other.entries().entrySet()) {
            Entry cur = entries.get(e.getKey());
            if (cur == null || e.getValue().version() >= cur.version()) {
                entries.put(e.getKey(), e.getValue());
                dirty.mark(e.getKey());
            }
        }
    }
}
