import com.purpurflow.common.control.*;
import com.purpurflow.common.protocol.*;
import com.purpurflow.common.state.Delta;
import com.purpurflow.server.config.Config;
import com.purpurflow.server.session.*;
import com.purpurflow.server.egress.*;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * 本地集成驱动（降级验证，非真实服务端）：直接调用生产代码，走完整
 * "HELLO 编码 → 握手协商 → HELLO_ACK 编码 → 出站编排 → 解码还原"链路，
 * 打印真实结果作为 06_live_demo.md 的【静态验证】证据。
 * 不伪造任何日志/指标；所有输出由下列 assert 保证真实。
 */
public class IntegrationDrive {
    static void check(boolean ok, String label) {
        System.out.printf("[%s] %s%n", ok ? "PASS" : "FAIL", label);
        if (!ok) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        System.out.println("=== PurpurFlow 本地集成驱动（stub 环境，降级验证）===");
        FrameCodec codec = new FrameCodec();
        Handshake hs = new Handshake(Config.from(new FileConfiguration()));
        UUID pid = UUID.randomUUID();
        long sessionId = System.currentTimeMillis(), epoch = 1L;

        // 场景2：握手 HELLO → HELLO_ACK
        hs.open(pid, sessionId, epoch);
        check(hs.session(pid) != null, "S2 进服创建会话");
        Capability cap = new Capability(ProtocolVersion.V1.id(), 1,
                Capability.FEATURE_ZIGZAG | Capability.FEATURE_DICT | Capability.FEATURE_DELTA, 32768);
        byte[] helloPayload = ControlFrame.encodeHello(new Hello(cap));
        Frame helloFrame = Frame.data(0, 1, 0, 1, Flags.RELIABLE_ORDERED | Flags.PRI_CONTROL,
                List.of(new Message(FrameType.HELLO, helloPayload)));
        byte[] helloWire = codec.encode(helloFrame);
        Frame decodedHello = codec.decodeFrame(helloWire);
        Hello decodedCap = ControlFrame.decodeHello(decodedHello.messages().get(0).payload());
        check(decodedCap.capability().protocolVersion() == ProtocolVersion.V1.id(), "S2 HELLO 编解码往返");
        HelloAck ack = hs.onHello(pid, decodedCap);
        check(ack != null && ack.enhanced(), "S2 协商成功且 enhanced=true（R-03 联动）");
        byte[] ackWire = ControlFrame.encodeHelloAck(ack);
        HelloAck decodedAck = ControlFrame.decodeHelloAck(ackWire);
        check(decodedAck.enhanced() && decodedAck.sessionId() == sessionId, "S2 HELLO_ACK 编解码往返");
        check(hs.session(pid).mode() == SessionMode.ENHANCED, "S2 会话进入 ENHANCED");

        // 场景4：出站编排（背压/优先级/限速）
        EgressPipeline ep = new EgressPipeline(16, 10_000_000L, 1000, 16, p -> true);
        List<String> order = new ArrayList<>();
        Frame ctl = Frame.data(sessionId, 2, 1, 1, Flags.PRI_CONTROL,
                List.of(new Message(FrameType.HELLO_ACK, ackWire)));
        Frame stateF = Frame.data(sessionId, 3, 1, 1, Flags.PRI_STATE,
                List.of(new Message(FrameType.DELTA, new byte[]{1, 2, 3})));
        ep.offer(stateF, Priority.STATE, codec.encode(stateF).length, pid, f -> { order.add("STATE"); return true; });
        ep.offer(ctl, Priority.CONTROL, codec.encode(ctl).length, pid, f -> { order.add("CONTROL"); return true; });
        int sent = ep.drain(8, f -> codec.encode(f).length);
        check(sent == 2 && order.get(0).equals("CONTROL"), "S4 控制帧优先 + 合包出队");
        check(ep.queueSize() == 0, "S4 出站队列排空");

        // 场景6：重连补偿判定
        SnapshotStore store = new SnapshotStore(8, 300);
        // 写入 12 条（超过日志上限 8），使最老 seq=4，从而 ack=0 落在日志之外。
        for (int i = 0; i < 12; i++) {
            Delta d = new Delta(i); d.put(i % 3, i, i); store.append(pid, i, d);
        }
        check(store.canResumeIncrementally(pid, 11), "S6 ack=11 可增量补发（H4）");
        check(!store.canResumeIncrementally(pid, 0), "S6 ack=0 超出日志窗口 → 需全量快照（H4 兜底）");

        // 场景8：回滚（总开关关闭 → 无增强帧）
        check(!hs.session(pid).canSendEnhanced(false), "S8 总开关关闭 → 无增强帧（H2 回滚）");

        System.out.println("=== 集成驱动完成：全部 PASS（本地 stub 环境，非真实试机）===");
    }
}
