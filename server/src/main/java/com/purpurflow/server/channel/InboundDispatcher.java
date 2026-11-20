package com.purpurflow.server.channel;

import com.purpurflow.common.control.*;
import com.purpurflow.common.protocol.*;
import com.purpurflow.common.state.Delta;
import com.purpurflow.server.egress.EgressPipeline;
import com.purpurflow.server.egress.Priority;
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

    // 修复 R-07：每玩家独立出站编排器（H3 要求按玩家独立队列/限速桶）。
    private final java.util.Map<java.util.UUID, EgressPipeline> egress = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean egressEnabled = false;
    // 由插件按 Config 注入的编排参数
    private int maxQueue, highWater, maxPackets;
    private long maxBytes;

    public InboundDispatcher(Handshake handshake, org.bukkit.plugin.Plugin plugin) {
        this.handshake = handshake;
        this.plugin = plugin;
        this.sender = (p, bytes) -> p.sendPluginMessage(plugin, ChannelRegistrar.CHANNEL, bytes);
    }

    /** 由插件按 Config 启用出站编排（H3）。关闭时 sendControl 退化为直发（H2 回滚）。 */
    public void configureEgress(boolean enabled, int maxQueue, long maxBytesPerSec,
                                int maxPacketsPerSec, int highWatermark) {
        this.egressEnabled = enabled;
        this.maxQueue = maxQueue;
        this.maxBytes = maxBytesPerSec;
        this.maxPackets = maxPacketsPerSec;
        this.highWater = highWatermark;
        if (!enabled) egress.clear();
    }

    public boolean egressEnabled() { return egressEnabled; }
    public EgressPipeline pipeline(java.util.UUID id) { return egress.get(id); }

    /** 由插件在 channel 可写/定时 tick 时调用：排空指定玩家的出站队列。 */
    public int drain(Player player, int maxFrames) {
        EgressPipeline ep = egress.get(player.getUniqueId());
        if (ep == null) return 0;
        return ep.drain(maxFrames, frame -> codec.encode(frame).length);
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
        if (egressEnabled) {
            // 修复 R-07：控制帧入每玩家编排队列，经背压/限速后由 drain 写回。
            EgressPipeline ep = egress.computeIfAbsent(player.getUniqueId(), id ->
                    new EgressPipeline(maxQueue, maxBytes, maxPackets, highWater,
                            p -> channelWritable.getOrDefault(id, true)));
            byte[] bytes = codec.encode(frame);
            ep.offer(frame, Priority.CONTROL, bytes.length, player,
                    f -> { sender.send(player, codec.encode(f)); return true; });
        } else {
            sender.send(player, codec.encode(frame)); // 回滚路径：直发
        }
    }

    /** 可写性探测结果缓存，由插件在 Netty 可写性变化时更新（H3，无额外端口）。 */
    private final java.util.Map<java.util.UUID, Boolean> channelWritable = new java.util.concurrent.ConcurrentHashMap<>();
    public void setWritable(java.util.UUID id, boolean writable) { channelWritable.put(id, writable); }

    /** 玩家离开时清理其编排器，避免无界增长（R-12）。 */
    public void removePlayer(java.util.UUID id) {
        egress.remove(id);
        channelWritable.remove(id);
    }

    private void onInvalidFrame(Exception e) {
        // 生产环境接入 Metrics 计数 + 日志降噪；骨架阶段静默丢弃，保证不崩服
    }
}
