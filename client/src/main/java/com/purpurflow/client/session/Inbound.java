package com.purpurflow.client.session;

import com.purpurflow.client.net.FlowPayload;
import com.purpurflow.common.protocol.*;
import com.purpurflow.common.control.*;
import com.purpurflow.common.state.Delta;

/**
 * 入站处理（client 收 server 帧）：解码 → 控制帧协商 → 业务帧应用本地镜像。
 * Fabric payload handler 在主线程执行，故此处只做轻量分发，不阻塞（H3/安全边界）。
 */
public final class Inbound {
    private final FrameCodec codec = new FrameCodec();
    private final LocalMirror mirror;
    private final AckReporter ack;
    private final VanillaFallback fallback;

    public Inbound(LocalMirror mirror, AckReporter ack, VanillaFallback fallback) {
        this.mirror = mirror;
        this.ack = ack;
        this.fallback = fallback;
    }

    public void handle(FlowPayload payload) {
        if (fallback.isVanilla()) return;
        Frame frame;
        try {
            frame = codec.decodeFrame(payload.data());
        } catch (FrameException e) {
            return; // 计入安全计数后丢弃
        }
        ack.note(frame.ack());
        for (Message m : frame.messages()) {
            switch (m.type()) {
                case DISABLED -> fallback.toVanilla("server disabled enhanced flow");
                case HELLO_ACK -> {
                    HelloAck helloAck = ControlFrame.decodeHelloAck(m.payload());
                    if (!helloAck.enhanced()) fallback.toVanilla("server declined");
                }
                case FULL_SNAPSHOT, DELTA -> {
                    // 业务骨架：delta 载荷的实际解码由业务 codec 完成；此处用占位 delta 触发 seq 去重
                    Delta delta = new Delta(frame.ack());
                    mirror.apply(frame.seq(), delta);
                }
                default -> { /* 业务消息由上层分发 */ }
            }
        }
    }
}
