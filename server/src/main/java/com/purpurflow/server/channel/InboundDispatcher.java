package com.purpurflow.server.channel;

import com.purpurflow.common.control.*;
import com.purpurflow.common.protocol.*;
import com.purpurflow.common.state.Delta;
import com.purpurflow.server.session.Handshake;
import com.purpurflow.server.session.PerPlayerSession;
import com.purpurflow.server.session.SessionMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * 入站分发：解码帧（白名单 schema），按类型分派。异常计入安全计数，绝不抛到主线程（H5/2.9）。
 */
public final class InboundDispatcher implements PluginMessageListener {
    private final FrameCodec codec = new FrameCodec();
    private final Handshake handshake;
    private final org.bukkit.plugin.Plugin plugin;
    private long securityInvalidFrames = 0;

    public InboundDispatcher(Handshake handshake, org.bukkit.plugin.Plugin plugin) {
        this.handshake = handshake;
        this.plugin = plugin;
        this.sender = (p, bytes) -> p.sendPluginMessage(plugin, ChannelRegistrar.CHANNEL, bytes);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!ChannelRegistrar.CHANNEL.equals(channel)) return;
        PerPlayerSession session = handshake.session(player.getUniqueId());
        if (session == null || session.mode() == SessionMode.VANILLA) return; // H2

        Frame frame;
        try {
            frame = codec.decodeFrame(message);
        } catch (FrameException e) {
            // 计入安全计数并丢弃，绝不抛出
            onInvalidFrame(e);
            return;
        }

        for (Message msg : frame.messages()) {
            try {
                dispatch(player, session, msg, frame.ack());
            } catch (RuntimeException e) {
                onInvalidFrame(e);
            }
        }
    }

    private void dispatch(Player player, PerPlayerSession s, Message msg, long ack) {
        s.noteAck(ack); // 捎带 ack
        switch (msg.type()) {
            case HELLO -> {
                Hello hello = ControlFrame.decodeHello(msg.payload());
                HelloAck ackFrame = handshake.onHello(player.getUniqueId(), hello);
                if (ackFrame != null) {
                    sendControl(player, FrameType.HELLO_ACK, ControlFrame.encodeHelloAck(ackFrame));
                }
            }
            case RESUME -> {
                // 交给 ResumeService（上层注入），此处仅示意
                Resume resume = ControlFrame.decodeResume(msg.payload());
                s.noteAck(resume.ackSeq());
            }
            case DICT_SYNC_ACK -> { /* 字典确认，进入稳态 */ }
            case HEARTBEAT -> { /* RTT 估算由上层记录时间戳 */ }
            default -> { /* 业务消息由上层 dispatcher 处理 */ }
        }
    }

    /** 由插件注入的实际发送器（避免本类直接依赖发送实现）。 */
    private Sender sender;

    public interface Sender {
        void send(Player player, byte[] bytes);
    }

    public void setSender(Sender sender) { this.sender = sender; }

    private void sendControl(Player player, FrameType type, byte[] payload) {
        int ctlFlags = Flags.RELIABLE_ORDERED | Flags.PRI_CONTROL;
        Frame frame = Frame.data(0L, 0L, 0L, 0, ctlFlags,
                java.util.List.of(new Message(type, payload)));
        sender.send(player, codec.encode(frame));
    }

    private void onInvalidFrame(Exception e) {
        // 生产环境接入 Metrics 计数 + 日志降噪；骨架阶段静默丢弃，保证不崩服
    }
}
