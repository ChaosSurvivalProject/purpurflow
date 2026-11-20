package com.purpurflow.server.egress;

/**
 * 背压门控（H3）：仅在 channel 可写且队列低于高水位时出队写。
 * 通过可写性谓词解耦具体 Netty channel，便于测试。
 */
public final class BackpressureGate {
    private final int highWatermark;
    private final WritabilityProbe probe;

    /** 可写性探测接口，由插件侧用实际 channel.isWritable() 实现。 */
    public interface WritabilityProbe {
        boolean isWritable(Object player);
    }

    public BackpressureGate(int highWatermark, WritabilityProbe probe) {
        this.highWatermark = highWatermark;
        this.probe = probe;
    }

    /** 判断当前是否允许入队/出队。 */
    public boolean canEmit(Object player, int currentQueueSize) {
        return probe.isWritable(player) && currentQueueSize < highWatermark;
    }

    public int highWatermark() { return highWatermark; }
}
