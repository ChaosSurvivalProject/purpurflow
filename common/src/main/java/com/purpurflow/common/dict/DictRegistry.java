package com.purpurflow.common.dict;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 双端共享字典注册表（L2）。保存多个版本快照，支持版本对齐与回退。
 * 线程安全：当前快照用 AtomicReference，历史快照不可变。
 */
public final class DictRegistry {
    private final AtomicReference<DictSnapshot> current;
    private final Map<Integer, DictSnapshot> history = new LinkedHashMap<>();

    public DictRegistry(DictSnapshot initial) {
        this.current = new AtomicReference<>(initial);
        this.history.put(initial.version(), initial);
    }

    public DictSnapshot current() { return current.get(); }
    public int currentVersion() { return current.get().version(); }

    /** 注册新版本快照（来自 DICT_SYNC 或服务端字典升级）。 */
    public synchronized void install(DictSnapshot snapshot) {
        history.put(snapshot.version(), snapshot);
        current.set(snapshot);
    }

    /** 版本回退：回滚到指定历史版本（回滚方案用）。 */
    public synchronized boolean rollbackTo(int version) {
        DictSnapshot s = history.get(version);
        if (s == null) return false;
        current.set(s);
        return true;
    }

    /** 若客户端版本落后，返回可从其版本升级到当前的增量；否则返回空。 */
    public Optional<List<String>> syncFrom(int clientVersion) {
        DictSnapshot base = history.get(clientVersion);
        if (base == null) return Optional.empty();       // 无法增量，需全量
        return Optional.of(current.get().diffFrom(base));
    }

    public int idOf(String s) { return current.get().idOf(s); }
    public String stringOf(int id) { return current.get().stringOf(id); }
}
