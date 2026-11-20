package com.purpurflow.client.compat;

/** vanilla 降级标记（H2/H5）：一旦降级，之后零增强流量。 */
public final class VanillaFallback {
    private volatile boolean vanilla = false;
    private volatile String reason = "";

    public void toVanilla(String reason) {
        this.vanilla = true;
        this.reason = reason;
    }

    public boolean isVanilla() { return vanilla; }
    public String reason() { return reason; }

    /** 是否允许发送增强帧：非 vanilla 且服务端支持。 */
    public boolean canSendEnhanced(boolean serverSupports) {
        return !vanilla && serverSupports;
    }
}
