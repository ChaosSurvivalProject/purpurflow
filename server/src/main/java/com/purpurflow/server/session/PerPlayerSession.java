package com.purpurflow.server.session;

import com.purpurflow.common.control.Capability;
import com.purpurflow.common.protocol.Message;
import com.purpurflow.common.state.DedupWindow;
import com.purpurflow.common.state.SeqTracker;
import com.purpurflow.server.egress.PriorityQueue;

import java.util.UUID;

/** 每玩家会话：模式、sessionId、序号、去重窗口、独立优先级队列（H2/H3/H4）。 */
public final class PerPlayerSession {
    private final UUID playerId;
    private final long sessionId;
    private final long epoch;
    private final SeqTracker seq = new SeqTracker();
    private final DedupWindow dedup;
    private final PriorityQueue queue;

    private volatile SessionMode mode = SessionMode.HANDSHAKING;
    private volatile Capability clientCapability;
    private volatile int negotiatedVersion = 0;
    private volatile int chunkBytes = 0;
    private volatile long lastAckSeq = -1;
    private volatile long handshakeDeadlineMs;

    public PerPlayerSession(UUID playerId, long sessionId, long epoch, int dedupWindow, int maxQueue) {
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.epoch = epoch;
        this.dedup = new DedupWindow(dedupWindow);
        this.queue = new PriorityQueue(maxQueue);
    }

    public UUID playerId() { return playerId; }
    public long sessionId() { return sessionId; }
    public long epoch() { return epoch; }
    public SeqTracker seq() { return seq; }
    public DedupWindow dedup() { return dedup; }
    public PriorityQueue queue() { return queue; }

    public SessionMode mode() { return mode; }
    public void setMode(SessionMode mode) { this.mode = mode; }

    public Capability clientCapability() { return clientCapability; }
    public void setClientCapability(Capability c) { this.clientCapability = c; }

    public int negotiatedVersion() { return negotiatedVersion; }
    public void setNegotiatedVersion(int v) { this.negotiatedVersion = v; }

    public int chunkBytes() { return chunkBytes; }
    public void setChunkBytes(int b) { this.chunkBytes = b; }

    public long lastAckSeq() { return lastAckSeq; }
    public void noteAck(long ackSeq) { this.lastAckSeq = Math.max(this.lastAckSeq, ackSeq); }

    public long handshakeDeadlineMs() { return handshakeDeadlineMs; }
    public void setHandshakeDeadline(long deadline) { this.handshakeDeadlineMs = deadline; }

    /** 是否允许下发增强帧：仅 ENHANCED 模式且总开关开启（H2）。 */
    public boolean canSendEnhanced(boolean globalEnabled) {
        return globalEnabled && mode == SessionMode.ENHANCED;
    }
}
