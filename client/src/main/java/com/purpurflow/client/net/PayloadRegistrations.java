package com.purpurflow.client.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Payload 注册（L0）：双端都要注册，S2C 用 playS2C，C2S 用 playC2S。
 * 1.21.11 提供 registerLarge 官方分包 API：超过 vanilla 1 MiB 下行限的大帧可分包。
 * 上行仅 32 KiB，不做 large 注册，靠自研分片（见 Fragmenter/Reassembler 语义）。
 */
public final class PayloadRegistrations {
    private PayloadRegistrations() {}

    public static void register() {
        // 下行：使用官方 registerLarge，允许超过 vanilla 单包限制的帧（分包由 Fabric 处理）
        // DATA_MAX 为运行时协商值；此处给保守上界，实际由 HELLO_ACK.serverMaxChunk 覆盖。
        PayloadTypeRegistry.playS2C().registerLarge(
                FlowPayload.TYPE, FlowPayload.CODEC, 4 * 1024 * 1024);
        // 上行：普通注册，受 32 KiB 限制，业务侧分片
        PayloadTypeRegistry.playC2S().register(
                FlowPayload.TYPE, FlowPayload.CODEC);
    }
}
