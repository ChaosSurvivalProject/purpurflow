package com.purpurflow.server.session;

import com.purpurflow.common.state.Delta;
import com.purpurflow.common.state.Snapshot;

import java.util.*;

/**
 * 快照与有限 seq 日志存储（H4）。按 session 保存快照与最近 N 条 delta，
 * 超时清理，保证内存上界。
 */
public final class SnapshotStore {
    private final int seqLogLength;
    private final int ttlSec;
    private final Map<UUID, Entry> store = new HashMap<>();

    private static final class Entry {
        final Snapshot snapshot = new Snapshot();
        final Deque<LoggedDelta> log = new ArrayDeque<>();
        long lastAccessMs = System.currentTimeMillis();
    }

    private record LoggedDelta(long seq, Delta delta) {}

    public SnapshotStore(int seqLogLength, int ttlSec) {
        this.seqLogLength = seqLogLength;
        this.ttlSec = ttlSec;
    }

    /** 记录一条带 seq 的 delta。 */
    public synchronized void append(UUID session, long seq, Delta delta) {
        Entry e = store.computeIfAbsent(session, k -> new Entry());
        e.snapshot.apply(delta);
        e.log.addLast(new LoggedDelta(seq, delta));
        while (e.log.size() > seqLogLength) e.log.removeFirst();
        e.lastAccessMs = System.currentTimeMillis();
    }

    public synchronized Snapshot snapshot(UUID session) {
        Entry e = store.get(session);
        return e == null ? null : e.snapshot;
    }

    /** 判断 ackSeq 之后的 delta 是否都还在日志里，可增量补发。 */
    public synchronized boolean canResumeIncrementally(UUID session, long ackSeq) {
        Entry e = store.get(session);
        if (e == null || e.log.isEmpty()) return false;
        long oldest = e.log.peekFirst().seq();
        return ackSeq >= oldest - 1;
    }

    /** 收集 ackSeq 之后的所有 delta，合并为一条增量。 */
    public synchronized Delta deltasAfter(UUID session, long ackSeq) {
        Entry e = store.get(session);
        if (e == null) return null;
        Delta merged = new Delta(ackSeq);
        for (LoggedDelta ld : e.log) {
            if (ld.seq() > ackSeq) merged.merge(ld.delta());
        }
        return merged;
    }

    /** 周期性清理过期会话，防止内存泄漏。 */
    public synchronized void evictExpired() {
        long now = System.currentTimeMillis();
        long cutoff = now - ttlSec * 1000L;
        store.entrySet().removeIf(en -> en.getValue().lastAccessMs < cutoff);
    }
}
