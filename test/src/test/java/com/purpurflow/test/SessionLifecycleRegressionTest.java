package com.purpurflow.test;

import com.purpurflow.common.control.Capability;
import com.purpurflow.common.control.Hello;
import com.purpurflow.common.control.HelloAck;
import com.purpurflow.common.control.ProtocolVersion;
import com.purpurflow.server.config.Config;
import com.purpurflow.server.session.Handshake;
import com.purpurflow.server.session.PerPlayerSession;
import com.purpurflow.server.session.SessionMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** R-06/R-12 会话生命周期回归：会话创建→握手协商→超时降级→断线清理。 */
class SessionLifecycleRegressionTest {

    private static Handshake newHandshake() {
        FileConfiguration c = new FileConfiguration(); // stub：全部返回 default
        return new Handshake(Config.from(c));
    }

    private static Hello compatibleHello() {
        Capability cap = new Capability(ProtocolVersion.V1.id(), 1,
                Capability.FEATURE_ZIGZAG | Capability.FEATURE_DICT | Capability.FEATURE_DELTA, 32768);
        return new Hello(cap);
    }

    /** 修复前：open 无调用点，session() 恒 null —— 此测试锁定期望行为。 */
    @Test
    void BUG_F_sessionCreatedAndHelloNegotiated() {
        Handshake h = newHandshake();
        UUID id = UUID.randomUUID();
        assertNull(h.session(id), "未进服前无会话");

        h.open(id, 1234L, 1L);
        PerPlayerSession s = h.session(id);
        assertNotNull(s, "进服后必须创建会话（R-06）");
        assertEquals(SessionMode.HANDSHAKING, s.mode());

        HelloAck ack = h.onHello(id, compatibleHello());
        assertNotNull(ack, "兼容客户端必须收到 HELLO_ACK");
        assertTrue(ack.enhanced(), "enhanced 必须为 true（R-03 联动）");
        assertEquals(SessionMode.ENHANCED, s.mode());
        assertTrue(s.canSendEnhanced(true), "ENHANCED 且总开关开时允许下发增强帧");
        assertFalse(s.canSendEnhanced(false), "总开关关时禁止下发（H2 回滚）");
    }

    /** 不兼容版本必须安全降级 vanilla、不踢人（H5）。 */
    @Test
    void BUG_F_incompatibleVersionFallsBackToVanilla() {
        Handshake h = newHandshake();
        UUID id = UUID.randomUUID();
        h.open(id, 1L, 1L);
        Capability bad = new Capability(99, 1, Capability.FEATURE_ZIGZAG, 1024); // 协议版本 99 不在白名单
        assertNull(h.onHello(id, new Hello(bad)), "不兼容版本返回 null（上层发 DISABLED）");
        assertEquals(SessionMode.VANILLA, h.session(id).mode());
        assertFalse(h.session(id).canSendEnhanced(true));
    }

    /** 修复后：expireAll 必须批量降级超时会话并返回计数。 */
    @Test
    void BUG_F_expireAllDowngradesTimedOutSessions() {
        Handshake h = newHandshake();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        h.open(a, 1L, 1L);
        h.open(b, 2L, 1L);
        // 手动把 deadline 设到过去
        h.session(a).setHandshakeDeadline(System.currentTimeMillis() - 1000);
        h.session(b).setHandshakeDeadline(System.currentTimeMillis() - 1000);
        int expired = h.expireAll();
        assertEquals(2, expired);
        assertEquals(SessionMode.VANILLA, h.session(a).mode());
        assertEquals(SessionMode.VANILLA, h.session(b).mode());
    }

    /** 修复后：close 必须真正移除会话，防止 Map 无界增长（R-12）。 */
    @Test
    void BUG_F_closeRemovesSession() {
        Handshake h = newHandshake();
        UUID id = UUID.randomUUID();
        h.open(id, 1L, 1L);
        assertEquals(1, h.sessionCount());
        h.close(id);
        assertNull(h.session(id));
        assertEquals(0, h.sessionCount());
    }
}
