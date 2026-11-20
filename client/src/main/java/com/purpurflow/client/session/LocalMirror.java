package com.purpurflow.client.session;

import com.purpurflow.common.state.Delta;
import com.purpurflow.common.state.DedupWindow;
import com.purpurflow.common.state.Snapshot;

/** 客户端本地状态镜像（H4）：按 seq 去重、按版本号 last-write-wins。 */
public final class LocalMirror {
    private final Snapshot snapshot = new Snapshot();
    private final DedupWindow dedup;

    public LocalMirror(int dedupWindow) {
        this.dedup = new DedupWindow(dedupWindow);
    }

    /** 应用一条 delta，返回是否为首次应用（重复/过期返回 false）。 */
    public boolean apply(long seq, Delta delta) {
        if (!dedup.isNew(seq)) return false;
        snapshot.apply(delta);
        return true;
    }

    /** 应用全量快照（重连兜底），先清空再重建。 */
    public void applyFull(Delta fullDelta) {
        dedup.clear();
        // Snapshot 未提供 clear，这里以全量覆盖语义应用（高版本胜出）；骨架阶段直接重建引用需外部配合
        snapshot.apply(fullDelta);
    }

    public Snapshot snapshot() { return snapshot; }
}
