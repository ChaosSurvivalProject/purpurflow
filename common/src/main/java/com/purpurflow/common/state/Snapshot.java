package com.purpurflow.common.state;

import java.util.HashMap;
import java.util.Map;

/**
 * 玩家状态快照：key → (value, version)。
 * 版本号用于 last-write-wins 幂等应用（H4）。
 */
public final class Snapshot {
    private final Map<Integer, long[]> fields = new HashMap<>(); // key -> {value, version}

    /** 写入一个字段，带版本号。 */
    public void put(int key, long value, long version) {
        long[] cur = fields.get(key);
        if (cur == null || version >= cur[1]) {
            fields.put(key, new long[]{value, version});
        }
    }

    public long value(int key) {
        long[] v = fields.get(key);
        return v == null ? 0 : v[0];
    }

    public long version(int key) {
        long[] v = fields.get(key);
        return v == null ? -1 : v[1];
    }

    /** 应用一个 delta：按 key 版本号 last-write-wins，重复应用无副作用（幂等）。 */
    public void apply(Delta delta) {
        for (Map.Entry<Integer, Delta.Entry> e : delta.entries().entrySet()) {
            put(e.getKey(), e.getValue().value(), e.getValue().version());
        }
    }

    /** 生成全量快照的 delta（所有字段），用于 FULL_SNAPSHOT / 重连兜底。 */
    public Delta fullDelta(long baseSeq) {
        Delta d = new Delta(baseSeq);
        for (Map.Entry<Integer, long[]> e : fields.entrySet()) {
            d.put(e.getKey(), e.getValue()[0], e.getValue()[1]);
        }
        return d;
    }

    public int size() { return fields.size(); }
}
