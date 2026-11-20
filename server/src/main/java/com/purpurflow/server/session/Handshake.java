package com.purpurflow.server.session;

import com.purpurflow.common.control.*;
import com.purpurflow.common.protocol.FrameType;
import com.purpurflow.server.config.Config;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 握手状态机（H5）：HELLO → 协商 → HELLO_ACK → [DICT_SYNC] → STEADY，
 * 超时/不匹配 → DISABLED（标记 vanilla，绝不踢人）。
 */
public final class Handshake {
    private final Config config;
    private final CapabilityNegotiator negotiator;
    private final Map<UUID, PerPlayerSession> sessions = new ConcurrentHashMap<>();
    private final Set<Integer> serverSupported = Set.of(ProtocolVersion.V1.id());

    public Handshake(Config config) {
        this.config = config;
        this.negotiator = new CapabilityNegotiator(config.whitelist, serverSupported);
    }

    public PerPlayerSession session(UUID id) { return sessions.get(id); }

    /** 玩家进入 play 阶段时创建会话，初始 HANDSHAKING，设定超时。 */
    public PerPlayerSession open(UUID playerId, long sessionId, long epoch) {
        PerPlayerSession s = new PerPlayerSession(playerId, sessionId, epoch,
                config.dedupWindow, config.maxQueuePerPlayer);
        s.setHandshakeDeadline(System.currentTimeMillis() + config.handshakeTimeoutMs);
        sessions.put(playerId, s);
        return s;
    }

    /** 处理 HELLO，返回应下发的 HELLO_ACK；协商失败返回 null 并标记 vanilla。 */
    public HelloAck onHello(UUID playerId, Hello hello) {
        PerPlayerSession s = sessions.get(playerId);
        if (s == null) return null;
        s.setClientCapability(hello.capability());

        ProtocolVersion v = negotiator.negotiate(hello.capability(), serverCapability());
        if (v == ProtocolVersion.DISABLED) {
            s.setMode(SessionMode.VANILLA);
            return null; // 上层发 DISABLED 帧，不踢人
        }
        s.setNegotiatedVersion(v.id());
        s.setChunkBytes(resolveChunk(hello.capability().localMaxChunk()));
        s.setMode(SessionMode.ENHANCED);

        int dict = config.dictionaryVersion;
        return new HelloAck(v.id(), s.sessionId(), dict, s.chunkBytes(), true);
    }

    /** 超时检查：超时仍未完成握手则降级 vanilla。 */
    public void checkTimeout(UUID playerId) {
        PerPlayerSession s = sessions.get(playerId);
        if (s != null && s.mode() == SessionMode.HANDSHAKING
                && System.currentTimeMillis() > s.handshakeDeadlineMs()) {
            s.setMode(SessionMode.VANILLA);
        }
    }

    /** 修复 R-06/R-12：由插件定时调用，批量降级超时会话，返回本次降级数量。 */
    public int expireAll() {
        int expired = 0;
        long now = System.currentTimeMillis();
        for (PerPlayerSession s : sessions.values()) {
            if (s.mode() == SessionMode.HANDSHAKING && now > s.handshakeDeadlineMs()) {
                s.setMode(SessionMode.VANILLA);
                expired++;
            }
        }
        return expired;
    }

    /** 当前会话数，供观测与回滚演练核对（H3）。 */
    public int sessionCount() { return sessions.size(); }

    public void close(UUID playerId) { sessions.remove(playerId); }

    private Capability serverCapability() {
        int features = Capability.FEATURE_ZIGZAG | Capability.FEATURE_DICT | Capability.FEATURE_DELTA
                | Capability.FEATURE_LARGE_PAYLOAD;
        if (config.compressionEnabled) features |= Capability.FEATURE_COMPRESSION;
        return new Capability(ProtocolVersion.V1.id(), config.dictionaryVersion, features, config.maxFrameBytes);
    }

    /** chunk=0 表示运行时探测：此处取客户端声明与服务端上限的较小值。 */
    private int resolveChunk(int clientChunk) {
        int base = config.fragmentChunkBytes;
        if (base > 0) return Math.min(base, config.maxFrameBytes);
        if (clientChunk > 0) return Math.min(clientChunk, config.maxFrameBytes);
        return Math.min(32 * 1024, config.maxFrameBytes); // 保守回退：上行 32 KiB 下限，仍非硬编码业务上限
    }
}
