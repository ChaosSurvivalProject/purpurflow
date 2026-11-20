package com.purpurflow.common.control;

/**
 * RESUME（C→S）：断线重连请求，携带 epoch 与已确认的最大 seq。
 * 服务端据此决定增量补发（DELTA fromSeq）还是全量快照。
 */
public final class Resume {
    private final long sessionId;
    private final long epoch;
    private final long ackSeq;

    public Resume(long sessionId, long epoch, long ackSeq) {
        this.sessionId = sessionId;
        this.epoch = epoch;
        this.ackSeq = ackSeq;
    }

    public long sessionId() { return sessionId; }
    public long epoch() { return epoch; }
    public long ackSeq() { return ackSeq; }
}
