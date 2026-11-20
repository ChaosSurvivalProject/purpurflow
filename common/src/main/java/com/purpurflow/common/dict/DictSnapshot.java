package com.purpurflow.common.dict;

import java.util.*;

/** 不可变字典快照：id ↔ 字符串 双向映射，版本化。 */
public final class DictSnapshot {
    private final int version;
    private final Map<String, Integer> idOf;
    private final List<String> stringOf;

    public DictSnapshot(int version, List<String> ordered) {
        this.version = version;
        this.stringOf = List.copyOf(ordered);
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            m.putIfAbsent(ordered.get(i), i);
        }
        this.idOf = Collections.unmodifiableMap(m);
    }

    public int version() { return version; }

    public int idOf(String s) {
        Integer id = idOf.get(s);
        if (id == null) throw new IllegalArgumentException("字典中不存在: " + s);
        return id;
    }

    public String stringOf(int id) {
        if (id < 0 || id >= stringOf.size()) throw new IllegalArgumentException("字典 id 越界: " + id);
        return stringOf.get(id);
    }

    public List<String> entries() { return stringOf; }
    public int size() { return stringOf.size(); }

    /** 计算从 base 到本快照的增量（新增的 id≥base.size 的条目）。 */
    public List<String> diffFrom(DictSnapshot base) {
        List<String> out = new ArrayList<>();
        for (int i = base.size(); i < stringOf.size(); i++) out.add(stringOf.get(i));
        return out;
    }

    /** 应用增量得到新快照（id 连续追加）。 */
    public DictSnapshot applyDelta(List<String> additions) {
        List<String> next = new ArrayList<>(stringOf);
        next.addAll(additions);
        return new DictSnapshot(version + 1, next);
    }
}
