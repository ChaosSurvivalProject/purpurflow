package com.purpurflow.test;

import com.purpurflow.common.state.Delta;
import com.purpurflow.server.session.SnapshotStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** R-12 快照存储回归：seq 日志上界、TTL 清理、增量补偿判定。 */
class SnapshotStoreRegressionTest {

    @Test
    void BUG_R12_seqLogBounded() {
        SnapshotStore store = new SnapshotStore(8, 300);
        UUID s = UUID.randomUUID();
        for (int i = 0; i < 100; i++) {
            Delta d = new Delta(i);
            d.put(i % 4, i, i);
            store.append(s, i, d);
        }
        // 日志上限 8；ack 之后若间隔过大应无法增量补发（需全量快照）
        assertFalse(store.canResumeIncrementally(s, 0), "ack=0 距最新 seq 太远，应判全量");
        assertTrue(store.canResumeIncrementally(s, 99), "ack=99 可增量");
        assertNull(store.snapshot(UUID.randomUUID()), "未知 session 返回 null");
    }

    @Test
    void BUG_R12_evictExpiredRemovesStaleSessions() throws Exception {
        SnapshotStore store = new SnapshotStore(8, 1); // TTL=1 秒
        UUID fresh = UUID.randomUUID(), stale = UUID.randomUUID();
        Delta d = new Delta(1);
        d.put(0, 1, 1);
        store.append(stale, 1, d);
        store.append(fresh, 1, d);
        assertEquals(2, countSessions(store, fresh, stale)); // 通过 snapshot 是否可判定

        Thread.sleep(1100); // 超过 1 秒 TTL
        store.append(fresh, 2, d); // 刷新 fresh 的 lastAccess
        store.evictExpired();
        assertNotNull(store.snapshot(fresh), "刚访问的会话须保留");
        assertNull(store.snapshot(stale), "超时会话须被清理（R-12）");
    }

    /** 借助两个已知 UUID 统计仍存活的会话数。 */
    private int countSessions(SnapshotStore store, UUID a, UUID b) {
        int n = 0;
        if (store.snapshot(a) != null) n++;
        if (store.snapshot(b) != null) n++;
        return n;
    }
}
