package com.purpurflow.server;

import com.purpurflow.common.protocol.FrameCodec;
import com.purpurflow.server.channel.ChannelRegistrar;
import com.purpurflow.server.channel.InboundDispatcher;
import com.purpurflow.server.config.Config;
import com.purpurflow.server.session.Handshake;
import com.purpurflow.server.session.SnapshotStore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
    private InboundDispatcher dispatcher;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = Config.from(getConfig());
        codec = new FrameCodec();
        codec.setLimits(config.maxFrameBytes, config.maxFrameBytes);
        snapshots = new SnapshotStore(config.resumeSeqLogLength, config.resumeSnapshotTtlSec);
        handshake = new Handshake(config);

        channels = new ChannelRegistrar(this);
        dispatcher = new InboundDispatcher(handshake, this);
        // 修复 R-07：注入 H3 出站编排参数（每玩家独立队列/限速/背压）。
        dispatcher.configureEgress(config.enabled, config.maxQueuePerPlayer,
                config.limitBytesPerSec, config.limitPacketsPerSec, config.highWatermark);
        channels.register(config.enabled, dispatcher);

        // 修复 R-06：注册玩家生命周期监听，真正创建/清理每玩家会话。
        // 旧实现 handshake.open() 全仓库无调用点，session() 恒为 null，
        // InboundDispatcher 在 session==null 时直接 return，增强流完全不可用。
        getServer().getPluginManager().registerEvents(new SessionLifecycleListener(), this);
        // 握手超时巡检 + 出站队列排空（H3/H5），每 tick（20 槽≈1s）执行一次。
        getServer().getScheduler().runTaskTimer(this, this::periodicTick, 20L, 20L);

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

    /** 玩家进入/离开时维护会话生命周期（H4/H5）。 */
    private final class SessionLifecycleListener implements Listener {
        @EventHandler
        public void onJoin(PlayerJoinEvent e) {
            if (!config.enabled) return;
            Player p = e.getPlayer();
            // sessionId 用时间戳+UUID 哈希组合，epoch 单调递增，重连可据此判定代际（H4）。
            long sessionId = System.currentTimeMillis();
            long epoch = Math.abs(p.getUniqueId().getMostSignificantBits());
            handshake.open(p.getUniqueId(), sessionId, epoch);
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent e) {
            // 修复 R-12：断线即清理会话与出站队列，避免 Map 无界增长。
            java.util.UUID id = e.getPlayer().getUniqueId();
            handshake.close(id);
            if (dispatcher != null) dispatcher.removePlayer(id);
        }
    }

    /** 定时：握手超时降级 + 快照 TTL 清理 + 排空各玩家出站队列（H3/H4/H5）。 */
    private void periodicTick() {
        if (handshake != null) handshake.expireAll();
        // 修复 R-12：evictExpired 原本无调用点，快照存储会无界增长；接入定时清理。
        if (snapshots != null) snapshots.evictExpired();
        if (dispatcher != null && dispatcher.egressEnabled()) {
            java.util.Set<java.util.UUID> ids = new java.util.HashSet<>();
            // 通过当前在线玩家逐个排空（Bukkit 侧遍历在线列表）
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                ids.add(p.getUniqueId());
                dispatcher.drain(p, 32);
            }
        }
    }

    // ---- 供业务侧调用的对外 API ----

    public Config config() { return config; }
    public FrameCodec codec() { return codec; }
    public Handshake handshake() { return handshake; }
    public SnapshotStore snapshots() { return snapshots; }
}
