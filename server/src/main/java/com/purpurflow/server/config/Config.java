package com.purpurflow.server.config;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.HashSet;
import java.util.Set;

/** 全部开关与阈值的强类型配置（默认值见 purpurflow.yml）。 */
public final class Config {
    public final boolean enabled;
    public final ProtocolWhitelist whitelist;
    public final int dictionaryVersion;

    public final boolean compressionEnabled;
    public final int compressionThreshold;

    public final int highWatermark;
    public final int maxQueuePerPlayer;
    public final long limitBytesPerSec;
    public final int limitPacketsPerSec;

    public final int resumeSeqLogLength;
    public final int resumeSnapshotTtlSec;
    public final long handshakeTimeoutMs;
    public final int fragmentChunkBytes;

    public final int maxFrameBytes;
    public final int dedupWindow;
    public final boolean packetEventsEnabled;

    private Config(FileConfiguration c) {
        this.enabled = c.getBoolean("enabled", true);
        Set<Integer> wl = new HashSet<>();
        for (Object o : c.getIntegerList("protocol-whitelist")) wl.add((Integer) o);
        if (wl.isEmpty()) wl.add(1);
        this.whitelist = new ProtocolWhitelist(wl);
        this.dictionaryVersion = c.getInt("dictionary-version", 1);

        this.compressionEnabled = c.getBoolean("compression.enabled", false);
        this.compressionThreshold = c.getInt("compression.threshold-bytes", 1024);

        this.highWatermark = c.getInt("backpressure.high-watermark", 1024);
        this.maxQueuePerPlayer = c.getInt("backpressure.max-queue-per-player", 2048);
        this.limitBytesPerSec = c.getLong("rate.limit-bytes-per-sec", 262144L);
        this.limitPacketsPerSec = c.getInt("rate.limit-packets-per-sec", 500);

        this.resumeSeqLogLength = c.getInt("resume.seq-log-length", 512);
        this.resumeSnapshotTtlSec = c.getInt("resume.snapshot-ttl-sec", 300);
        this.handshakeTimeoutMs = c.getLong("handshake.timeout-ms", 3000L);
        this.fragmentChunkBytes = c.getInt("fragment.chunk-bytes", 0);

        this.maxFrameBytes = c.getInt("security.max-frame-bytes", 1_048_576);
        this.dedupWindow = c.getInt("security.dedup-window", 256);
        this.packetEventsEnabled = c.getBoolean("optional.packetevents.enabled", false);
    }

    /** 从 Bukkit FileConfiguration 读取；插件负责 saveDefaultConfig 后调用。 */
    public static Config from(FileConfiguration c) {
        return new Config(c);
    }
}
