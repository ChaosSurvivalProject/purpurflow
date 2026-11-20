package com.purpurflow.client.session;

import com.purpurflow.common.control.Resume;

/** 重连恢复（H4）：断线后发送 RESUME(epoch, ackSeq) 请求增量补发。 */
public final class ResumeHandler {
    private final AckReporter ack;
    private final long sessionId;

    public ResumeHandler(AckReporter ack, long sessionId) {
        this.ack = ack;
        this.sessionId = sessionId;
    }

    public Resume build(long epoch) {
        return new Resume(sessionId, epoch, ack.current());
    }
}
