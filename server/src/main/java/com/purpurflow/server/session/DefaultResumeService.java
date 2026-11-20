package com.purpurflow.server.session;

import java.util.UUID;

/** ResumeService 默认实现：命中则增量，否则全量快照。 */
public final class DefaultResumeService implements ResumeService {
    private final SnapshotStore store;
    private final long currentSeq;

    public DefaultResumeService(SnapshotStore store, long currentSeq) {
        this.store = store;
        this.currentSeq = currentSeq;
    }

    @Override
    public ResumePlan resume(UUID sessionId, long epoch, long ackSeq) {
        if (ackSeq >= currentSeq) return ResumePlan.none();       // 已是最新
        if (store.canResumeIncrementally(sessionId, ackSeq)) {
            return ResumePlan.delta(ackSeq);
        }
        return ResumePlan.full();                                 // 日志被裁剪/未命中
    }
}
