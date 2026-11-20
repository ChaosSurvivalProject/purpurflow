package com.purpurflow.server.session;

import com.purpurflow.common.control.Capability;
import com.purpurflow.common.control.ProtocolVersion;
import com.purpurflow.server.config.ProtocolWhitelist;

import java.util.Set;

/**
 * 能力协商（H5 关键接口契约）：
 * negotiate(client, server) -> ProtocolVersion | DISABLED。
 * 服务端用白名单选出共同最高版本，降版本重试一次，仍不匹配返回 DISABLED。
 */
public final class CapabilityNegotiator {
    private final ProtocolWhitelist whitelist;
    private final Set<Integer> serverSupported;

    public CapabilityNegotiator(ProtocolWhitelist whitelist, Set<Integer> serverSupported) {
        this.whitelist = whitelist;
        this.serverSupported = Set.copyOf(serverSupported);
    }

    public ProtocolVersion negotiate(Capability client, Capability server) {
        if (client == null || server == null) return ProtocolVersion.DISABLED;
        int cv = client.protocolVersion();
        if (!serverSupported.contains(cv) && !whitelist.accepts(cv)) {
            return ProtocolVersion.DISABLED;
        }
        return whitelist.select(cv, serverSupported);
    }

    public int intersectFeatures(Capability a, Capability b) {
        return a.intersectFeatures(b);
    }
}
