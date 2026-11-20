package com.purpurflow.server;

import com.purpurflow.common.protocol.FrameCodec;
import com.purpurflow.server.channel.ChannelRegistrar;
import com.purpurflow.server.channel.InboundDispatcher;
import com.purpurflow.server.config.Config;
import com.purpurflow.server.session.Handshake;
import com.purpurflow.server.session.SnapshotStore;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PurpurFlow 插件入口。
 * 完全使用官方 API（Messenger / plugin messaging），不碰 NMS，零额外端口（H1）。
 */
public final class PurpurFlowPlugin extends JavaPlugin {
    private Config config;
    private FrameCodec codec;
    private Handshake handshake;
    private SnapshotStore snapshots;
    private ChannelRegistrar channels;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = Config.from(getConfig());
        codec = new FrameCodec();
        codec.setLimits(config.maxFrameBytes, config.maxFrameBytes);
        snapshots = new SnapshotStore(config.resumeSeqLogLength, config.resumeSnapshotTtlSec);
        handshake = new Handshake(config);

        channels = new ChannelRegistrar(this);
        InboundDispatcher dispatcher = new InboundDispatcher(handshake, this);
        channels.register(config.enabled, dispatcher);

        if (!config.enabled) {
            getLogger().info("PurpurFlow 已禁用（enabled=false），行为与原版一致。");
            return;
        }
        if (config.packetEventsEnabled) {
            // 可选 ② 拦截层：本骨架默认关闭，接入点预留；需运行时核对 PacketEvents 版本（见 docs/01）
            getLogger().warning("可选 PacketEvents 层已开启：请确认 1.21.11 适配与维护状态。");
        }
        getLogger().info("PurpurFlow 已启用，通道=" + ChannelRegistrar.CHANNEL);
    }

    @Override
    public void onDisable() {
        if (channels != null) channels.unregister();
    }

    // ---- 供业务侧调用的对外 API ----

    public Config config() { return config; }
    public FrameCodec codec() { return codec; }
    public Handshake handshake() { return handshake; }
    public SnapshotStore snapshots() { return snapshots; }
}
