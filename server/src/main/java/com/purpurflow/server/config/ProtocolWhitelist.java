package com.purpurflow.server.config;

import com.purpurflow.common.control.ProtocolVersion;
import java.util.Set;

/** 协议版本白名单（H5）。只有白名单内版本才允许协商，否则 DISABLED。 */
public final class ProtocolWhitelist {
    private final Set<Integer> allowed;

    public ProtocolWhitelist(Set<Integer> allowed) {
        this.allowed = Set.copyOf(allowed);
    }

    public boolean accepts(int versionId) {
        return allowed.contains(versionId);
    }

    /** 从客户端版本出发，选出双方共同支持的最高版本；无则返回 DISABLED。 */
    public ProtocolVersion select(int clientVersion, Set<Integer> serverSupported) {
        for (int v : serverSupported.stream().sorted(java.util.Comparator.reverseOrder()).toList()) {
            if (v == clientVersion && accepts(v)) return ProtocolVersion.fromId(v);
        }
        // 降版本重试一次：取客户端版本以下的最高白名单版本
        for (int v : serverSupported.stream().filter(x -> x < clientVersion)
                .sorted(java.util.Comparator.reverseOrder()).toList()) {
            if (accepts(v)) return ProtocolVersion.fromId(v);
        }
        return ProtocolVersion.DISABLED;
    }
}
