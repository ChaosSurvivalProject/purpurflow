package com.purpurflow.client.compat;

import com.purpurflow.client.net.FlowPayload;
import com.purpurflow.common.control.Capability;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 能力探测（L6/H2）：用 canSend 确认服务端是否声明支持该通道。
 * 探测失败即 vanilla 降级，不重试、不刷屏。
 */
public final class CapabilityProbe {
    private CapabilityProbe() {}

    public static final int FEATURE_ZIGZAG = Capability.FEATURE_ZIGZAG;
    public static final int FEATURE_DICT = Capability.FEATURE_DICT;
    public static final int FEATURE_DELTA = Capability.FEATURE_DELTA;
    public static final int FEATURE_COMPRESSION = Capability.FEATURE_COMPRESSION;

    /** 服务端是否支持 purpurflow:main 通道。 */
    public static boolean serverSupports() {
        return ClientPlayNetworking.canSend(FlowPayload.TYPE);
    }

    /** 构造客户端能力位图（版本、字典、特性、本地分片上限）。 */
    public static Capability localCapability(int dictVersion, int localMaxChunk) {
        int features = FEATURE_ZIGZAG | FEATURE_DICT | FEATURE_DELTA;
        return new Capability(1, dictVersion, features, localMaxChunk);
    }
}
